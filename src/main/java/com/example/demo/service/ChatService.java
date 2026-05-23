package com.example.demo.service;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ConversationResponse;
import com.example.demo.dto.GroupCreateRequest;
import com.example.demo.dto.GroupUpdateRequest;
import com.example.demo.dto.MessageResponse;
import com.example.demo.entity.Conversation;
import com.example.demo.entity.Message;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserSettings;
import com.example.demo.enums.NotificationType;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.FriendRepository;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ProfileRepository profileRepository;
    private final ProfileService profileService;
    private final S3Service s3Service;
    private final NotificationService notificationService;
    private final UserSettingsRepository userSettingsRepository;
    private final FriendRepository friendRepository;

    // Used by the WebSocket endpoint (text only — WS cannot carry multipart)
    @Transactional
    public MessageResponse sendMessage(String senderEmail, ChatRequest request) {
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("Message must have content");
        }
        try {
            return sendMessage(senderEmail, request.getCid(), request.getRecipientId(),
                    request.getContent(), null);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected failure sending text message", e);
        }
    }

    // Used by the REST endpoints (supports image upload)
    @Transactional
    public MessageResponse sendMessage(String senderEmail, UUID cid, UUID recipientId,
            String content, MultipartFile image) throws IOException {
        Profile sender = profileRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));

        Conversation conversation;
        if (cid != null) {
            conversation = conversationRepository.findById(cid)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
            boolean senderIsParticipant = conversation.getParticipants().stream()
                    .anyMatch(p -> p.getId().equals(sender.getId()));
            if (!senderIsParticipant) {
                throw new IllegalArgumentException("Access denied");
            }
            if (!conversation.isGroup()) {
                conversation.getParticipants().stream()
                        .filter(participant -> !participant.getId().equals(sender.getId()))
                        .findFirst()
                        .ifPresent(recipient -> enforceDirectMessagePrivacy(sender, recipient));
            }
        } else if (recipientId != null) {
            Profile recipient = profileRepository.findById(recipientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));
            enforceDirectMessagePrivacy(sender, recipient);

            conversation = conversationRepository.findBetweenUsers(sender.getId(), recipient.getId())
                    .orElseGet(() -> {
                        Conversation newConv = Conversation.builder()
                                .participants(new ArrayList<>(Arrays.asList(sender, recipient)))
                                .build();
                        return conversationRepository.save(newConv);
                    });
        } else {
            throw new IllegalArgumentException("Either conversation ID or recipient ID must be provided");
        }

        if (content == null && (image == null || image.isEmpty())) {
            throw new IllegalArgumentException("Message must have content or an image");
        }

        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            s3Service.validateImageFile(image);
            imageUrl = s3Service.uploadFile(image, "images");
        }

        try {
            Message message = Message.builder()
                    .conversation(conversation)
                    .sender(sender)
                    .content(content)
                    .imageUrl(imageUrl)
                    .isRead(false)
                    .build();

            message = messageRepository.save(message);

            String preview = message.getContent() != null ? message.getContent() : "Image";
            conversation.setLastMessagePreview(preview);
            conversation.setLastMessageAt(LocalDateTime.now());
            conversationRepository.save(conversation);

            createMessageNotifications(conversation, sender, preview);

            return mapToMessageResponse(message);
        } catch (Exception e) {
            if (imageUrl != null) {
                String key = s3Service.extractKey(imageUrl);
                if (key != null) {
                    try {
                        s3Service.deleteFile(key);
                    } catch (Exception ex) {
                        log.warn("Failed to clean up S3 image after failed message save: {}", key, ex);
                    }
                }
            }
            throw e;
        }
    }

    @Transactional
    public ConversationResponse findOrCreateDm(String senderEmail, UUID recipientId) {
        Profile sender = profileRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));
        Profile recipient = profileRepository.findById(recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));
        enforceDirectMessagePrivacy(sender, recipient);

        Conversation conversation = conversationRepository.findBetweenUsers(sender.getId(), recipient.getId())
                .orElseGet(() -> {
                    Conversation newConv = Conversation.builder()
                            .participants(new ArrayList<>(Arrays.asList(sender, recipient)))
                            .build();
                    return conversationRepository.save(newConv);
                });

        return mapToConversationResponse(conversation, sender.getId());
    }

    @Transactional
    public ConversationResponse createGroupConversation(String creatorEmail, GroupCreateRequest request) {
        if (request.getGroupName() == null || request.getGroupName().isBlank()) {
            throw new IllegalArgumentException("Group name must not be blank");
        }
        if (request.getParticipantIds() == null || request.getParticipantIds().size() < 2) {
            throw new IllegalArgumentException("A group conversation requires at least 2 other participants");
        }

        Profile creator = profileRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        List<Profile> participants = new ArrayList<>();
        participants.add(creator);

        for (UUID participantId : request.getParticipantIds()) {
            Profile participant = profileRepository.findById(participantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Participant not found: " + participantId));
            participants.add(participant);
        }

        Conversation conversation = Conversation.builder()
                .isGroup(true)
                .groupName(request.getGroupName())
                .groupAvatar(request.getGroupAvatar())
                .participants(participants)
                .build();

        conversation = conversationRepository.save(conversation);
        return mapToConversationResponse(conversation, creator.getId());
    }

    @Transactional
    public ConversationResponse addParticipant(UUID conversationId, UUID profileId, String requesterEmail) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Cannot add participants to a direct message conversation");
        }

        boolean requesterIsParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getEmail().equals(requesterEmail));
        if (!requesterIsParticipant) {
            throw new IllegalArgumentException("Only existing participants can add members");
        }

        Profile newMember = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found: " + profileId));

        boolean alreadyMember = conversation.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(profileId));
        if (alreadyMember) {
            throw new IllegalArgumentException("User is already a participant");
        }

        UUID requesterId = conversation.getParticipants().stream()
                .filter(p -> p.getEmail().equals(requesterEmail))
                .findFirst().map(Profile::getId).orElse(null);
        conversation.addParticipant(newMember);
        conversation = conversationRepository.save(conversation);
        return mapToConversationResponse(conversation, requesterId);
    }

    @Transactional
    public void removeParticipant(UUID conversationId, UUID targetProfileId, String requesterEmail) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Cannot remove participants from a direct message conversation");
        }

        Profile requester = profileRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        boolean requesterIsParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(requester.getId()));
        if (!requesterIsParticipant) {
            throw new IllegalArgumentException("Access denied");
        }

        // Participants may only remove themselves (leave) or be removed if the requester is removing another member.
        // Any participant can remove any other participant (no dedicated admin role in this version).
        Profile target = conversation.getParticipants().stream()
                .filter(p -> p.getId().equals(targetProfileId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Target user is not a participant"));

        conversation.removeParticipant(target);
        conversationRepository.save(conversation);
    }

    @Transactional
    public ConversationResponse updateGroup(UUID conversationId, GroupUpdateRequest request, String requesterEmail) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Cannot update a direct message conversation");
        }

        boolean requesterIsParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getEmail().equals(requesterEmail));
        if (!requesterIsParticipant) {
            throw new IllegalArgumentException("Access denied");
        }

        if (request.getGroupName() != null && !request.getGroupName().isBlank()) {
            conversation.setGroupName(request.getGroupName());
        }
        if (request.getGroupAvatar() != null) {
            conversation.setGroupAvatar(request.getGroupAvatar());
        }

        UUID requesterId = conversation.getParticipants().stream()
                .filter(p -> p.getEmail().equals(requesterEmail))
                .findFirst().map(Profile::getId).orElse(null);
        conversation = conversationRepository.save(conversation);
        return mapToConversationResponse(conversation, requesterId);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getUserConversations(String email, Pageable pageable) {
        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        UUID requesterId = profile.getId();
        return conversationRepository.findByParticipantId(requesterId, pageable)
                .map(c -> mapToConversationResponse(c, requesterId));
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationMessages(UUID conversationId, String email, Pageable pageable) {
        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(profile.getId()));
        if (!isParticipant) {
            throw new IllegalArgumentException("Access denied");
        }

        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(this::mapToMessageResponse);
    }

    @Transactional(readOnly = true)
    public void validateParticipant(UUID conversationId, String email) {
        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(profile.getId()));
        if (!isParticipant) {
            throw new IllegalArgumentException("Access denied");
        }
    }

    @Transactional
    public void deleteMessage(UUID conversationId, UUID messageId, String senderEmail) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        if (!message.getConversation().getId().equals(conversationId)) {
            throw new IllegalArgumentException("Message does not belong to this conversation");
        }

        if (!message.getSender().getEmail().equals(senderEmail)) {
            throw new IllegalArgumentException("Unauthorized to delete this message");
        }

        String imageUrl = message.getImageUrl();
        messageRepository.delete(message);

        if (imageUrl != null) {
            String key = s3Service.extractKey(imageUrl);
            if (key != null) {
                try {
                    s3Service.deleteFile(key);
                } catch (Exception e) {
                    log.warn("Failed to delete message image from S3: {}", key, e);
                }
            }
        }
    }

    @Transactional
    public void markAsRead(UUID conversationId, String email) {
        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(profile.getId()));
        if (!isParticipant) {
            throw new IllegalArgumentException("Access denied");
        }

        messageRepository.markAllAsRead(conversationId);
    }

    private MessageResponse mapToMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .sender(profileService.mapToResponse(message.getSender()))
                .content(message.getContent())
                .imageUrl(message.getImageUrl())
                .createdAt(message.getCreatedAt())
                .isRead(message.isRead())
                .build();
    }

    private void createMessageNotifications(Conversation conversation, Profile sender, String preview) {
        String title = conversation.isGroup() && conversation.getGroupName() != null
                ? "New message in " + conversation.getGroupName()
                : "New message";
        String body = profileService.displayName(sender) + ": " + preview;
        String targetUrl = "/conversations/" + conversation.getId();

        conversation.getParticipants().stream()
                .filter(participant -> !participant.getId().equals(sender.getId()))
                .forEach(participant -> notificationService.createNotification(
                        participant.getId(),
                        sender.getId(),
                        NotificationType.MESSAGE,
                        title,
                        body,
                        targetUrl));
    }

    private void enforceDirectMessagePrivacy(Profile sender, Profile recipient) {
        if (sender.getId().equals(recipient.getId())) {
            return;
        }

        String allowMessages = userSettingsRepository.findByProfileId(recipient.getId())
                .map(UserSettings::getPrivacySettings)
                .map(settings -> settings.getAllowMessages())
                .orElse("everyone");

        String normalized = allowMessages == null
                ? "everyone"
                : allowMessages.trim().toLowerCase(Locale.ROOT);

        switch (normalized) {
            case "everyone":
                return;
            case "friends":
            case "following":
                if (friendRepository.areFriends(sender.getId(), recipient.getId())) {
                    return;
                }
                throw new IllegalArgumentException("Recipient only accepts messages from friends");
            case "none":
                throw new IllegalArgumentException("Recipient is not accepting direct messages");
            default:
                log.warn("Unknown message privacy setting '{}' for profile {}; defaulting to everyone",
                        allowMessages, recipient.getId());
        }
    }

    private ConversationResponse mapToConversationResponse(Conversation conversation, UUID requesterId) {
        int unreadCount = requesterId != null
                ? messageRepository.countUnread(conversation.getId(), requesterId)
                : 0;
        return ConversationResponse.builder()
                .id(conversation.getId())
                .participants(conversation.getParticipants().stream()
                        .map(profileService::mapToResponse)
                        .collect(Collectors.toList()))
                .isGroup(conversation.isGroup())
                .groupName(conversation.getGroupName())
                .groupAvatar(conversation.getGroupAvatar())
                .unreadCount(unreadCount)
                .lastMessagePreview(conversation.getLastMessagePreview())
                .lastMessageAt(conversation.getLastMessageAt())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
}
