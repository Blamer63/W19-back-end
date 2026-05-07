package com.example.demo.service;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ConversationResponse;
import com.example.demo.dto.MessageResponse;
import com.example.demo.entity.Conversation;
import com.example.demo.entity.Message;
import com.example.demo.entity.Profile;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.ProfileRepository;
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
        } else if (recipientId != null) {
            Profile recipient = profileRepository.findById(recipientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));

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

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getUserConversations(String email, Pageable pageable) {
        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        return conversationRepository.findByParticipantId(profile.getId(), pageable)
                .map(this::mapToConversationResponse);
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

    @Transactional
    public void deleteMessage(UUID messageId, String senderEmail) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

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

        List<Message> unread = messageRepository.findByConversationIdAndIsReadFalse(conversationId);
        unread.forEach(m -> m.setRead(true));
        messageRepository.saveAll(unread);
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

    private ConversationResponse mapToConversationResponse(Conversation conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .participants(conversation.getParticipants().stream()
                        .map(profileService::mapToResponse)
                        .collect(Collectors.toList()))
                .lastMessagePreview(conversation.getLastMessagePreview())
                .lastMessageAt(conversation.getLastMessageAt())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
}
