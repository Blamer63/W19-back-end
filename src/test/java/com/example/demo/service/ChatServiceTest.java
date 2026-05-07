package com.example.demo.service;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ConversationResponse;
import com.example.demo.dto.MessageResponse;
import com.example.demo.dto.ProfileResponse;
import com.example.demo.entity.Conversation;
import com.example.demo.entity.Message;
import com.example.demo.entity.Profile;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private ProfileService profileService;
    @Mock private S3Service s3Service;

    // Constructed explicitly in setUp() so the exact @Mock instances above are injected.
    // @InjectMocks was removed because Mockito's constructor-injection strategy can silently
    // fall back to a different strategy when @RequiredArgsConstructor is involved, causing
    // the stubbed mock instances to differ from those used by the service.
    private ChatService chatService;

    private Profile sender;
    private Profile recipient;
    private Conversation conversation;
    private Message message;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                conversationRepository,
                messageRepository,
                profileRepository,
                profileService,
                s3Service
        );

        sender = new Profile();
        sender.setId(UUID.randomUUID());
        sender.setUsername("sender");
        sender.setEmail("sender@example.com");

        recipient = new Profile();
        recipient.setId(UUID.randomUUID());
        recipient.setUsername("recipient");
        recipient.setEmail("recipient@example.com");

        conversation = Conversation.builder()
                .participants(Arrays.asList(sender, recipient))
                .build();
        conversation.setId(UUID.randomUUID());

        message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .content("Hello")
                .build();
        message.setId(UUID.randomUUID());
    }

    // -------------------------------------------------------------------------
    // sendMessage
    // -------------------------------------------------------------------------

    @Test
    void sendMessage_NewConversation_Success() {
        ChatRequest request = new ChatRequest();
        request.setRecipientId(recipient.getId());
        request.setContent("Hello");

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(profileRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(conversationRepository.findBetweenUsers(sender.getId(), recipient.getId()))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
        when(messageRepository.save(any(Message.class))).thenReturn(message);
        when(profileService.mapToResponse(any(Profile.class))).thenReturn(new ProfileResponse());

        MessageResponse response = chatService.sendMessage("sender@example.com", request);

        assertNotNull(response);
        assertEquals("Hello", response.getContent());
        // once for the new conversation, once to update lastMessagePreview
        verify(conversationRepository, times(2)).save(any(Conversation.class));
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void sendMessage_ExistingConversation_Success() {
        ChatRequest request = new ChatRequest();
        request.setCid(conversation.getId());
        request.setContent("Hello again");

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenReturn(message);
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
        when(profileService.mapToResponse(any(Profile.class))).thenReturn(new ProfileResponse());

        MessageResponse response = chatService.sendMessage("sender@example.com", request);

        assertNotNull(response);
        verify(conversationRepository, never()).findBetweenUsers(any(), any());
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void sendMessage_RecipientNotFound_ThrowsException() {
        ChatRequest request = new ChatRequest();
        request.setRecipientId(UUID.randomUUID());
        request.setContent("Hello");

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(profileRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> chatService.sendMessage("sender@example.com", request));
    }

    @Test
    void sendMessage_BlankContent_ThrowsException() {
        ChatRequest request = new ChatRequest();
        request.setRecipientId(recipient.getId());
        request.setContent("   ");

        assertThrows(IllegalArgumentException.class,
                () -> chatService.sendMessage("sender@example.com", request));

        verifyNoInteractions(profileRepository, conversationRepository, messageRepository);
    }

    // -------------------------------------------------------------------------
    // getUserConversations
    // -------------------------------------------------------------------------

    @Test
    void getUserConversations_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Conversation> page = new PageImpl<>(Collections.singletonList(conversation));

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(conversationRepository.findByParticipantId(sender.getId(), pageable)).thenReturn(page);
        when(profileService.mapToResponse(any(Profile.class))).thenReturn(new ProfileResponse());

        Page<ConversationResponse> response =
                chatService.getUserConversations("sender@example.com", pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    // -------------------------------------------------------------------------
    // getConversationMessages
    // -------------------------------------------------------------------------

    @Test
    void getConversationMessages_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Message> page = new PageImpl<>(Collections.singletonList(message));

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(conversation.getId(), pageable))
                .thenReturn(page);
        when(profileService.mapToResponse(any(Profile.class))).thenReturn(new ProfileResponse());

        Page<MessageResponse> response =
                chatService.getConversationMessages(conversation.getId(), "sender@example.com", pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    void getConversationMessages_NonParticipant_ThrowsException() {
        Profile stranger = new Profile();
        stranger.setId(UUID.randomUUID());
        stranger.setEmail("stranger@example.com");

        Pageable pageable = PageRequest.of(0, 10);

        when(profileRepository.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.getConversationMessages(
                        conversation.getId(), "stranger@example.com", pageable));

        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtDesc(any(), any());
    }

    // -------------------------------------------------------------------------
    // markAsRead
    // -------------------------------------------------------------------------

    @Test
    void markAsRead_Success() {
        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        chatService.markAsRead(conversation.getId(), "sender@example.com");

        verify(messageRepository).markAllAsRead(conversation.getId());
        verify(messageRepository, never()).saveAll(any());
    }

    @Test
    void markAsRead_NonParticipant_ThrowsException() {
        Profile stranger = new Profile();
        stranger.setId(UUID.randomUUID());
        stranger.setEmail("stranger@example.com");

        when(profileRepository.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.markAsRead(conversation.getId(), "stranger@example.com"));

        verify(messageRepository, never()).markAllAsRead(any());
        verify(messageRepository, never()).saveAll(any());
    }
}
