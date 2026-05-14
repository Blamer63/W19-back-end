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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ProfileRepository profileRepository;
    private final ProfileService profileService;

    @Transactional
    public MessageResponse sendMessage(String senderEmail, ChatRequest request) {
        Profile sender = profileRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));

        Conversation conversation;
        if (request.getCid() != null) {
            conversation = conversationRepository.findById(request.getCid())
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        } else if (request.getRecipientId() != null) {
            Profile recipient = profileRepository.findById(request.getRecipientId())
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

        // If no content provided, just create/find the conversation without sending a message
        if (request.getContent() == null || request.getContent().isBlank()) {
            return MessageResponse.builder()
                    .id(null)
                    .conversationId(conversation.getId())
                    .sender(profileService.mapToResponse(sender))
                    .content("")
                    .createdAt(LocalDateTime.now())
                    .isRead(false)
                    .build();
        }

        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .content(request.getContent())
                .isRead(false)
                .build();

        message = messageRepository.save(message);

        conversation.setLastMessagePreview(message.getContent());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        return mapToMessageResponse(message);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getUserConversations(String email, Pageable pageable) {
        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        return conversationRepository.findByParticipantId(profile.getId(), pageable)
                .map(this::mapToConversationResponse);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationMessages(UUID conversationId, Pageable pageable) {
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(this::mapToMessageResponse);
    }

    @Transactional
    public void markAsRead(UUID conversationId, String email) {
        // Find existing messages in this conversation that are not sent by current user and are unread
        // For simplicity in this mock-aligned version, we mark all unread in this conversation
        List<Message> unreadMessages = messageRepository.findAll() 
                .stream()
                .filter(m -> m.getConversation().getId().equals(conversationId) && !m.isRead())
                .collect(Collectors.toList());

        unreadMessages.forEach(m -> m.setRead(true));
        messageRepository.saveAll(unreadMessages);
    }

    private MessageResponse mapToMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .sender(profileService.mapToResponse(message.getSender()))
                .content(message.getContent())
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
