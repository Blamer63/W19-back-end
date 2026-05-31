package com.example.demo.service;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ConversationResponse;
import com.example.demo.dto.GroupCreateRequest;
import com.example.demo.dto.GroupUpdateRequest;
import com.example.demo.dto.MessageResponse;
import com.example.demo.dto.ProfileResponse;
import com.example.demo.entity.Conversation;
import com.example.demo.entity.Message;
import com.example.demo.entity.Profile;
import com.example.demo.entity.PrivacySettings;
import com.example.demo.entity.UserSettings;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.FriendRepository;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.UserSettingsRepository;
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
    @Mock private NotificationService notificationService;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private FriendRepository friendRepository;

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
                s3Service,
                notificationService,
                userSettingsRepository,
                friendRepository
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
    void sendMessage_NewDirectMessage_ShouldNotReuseGroupConversation() {
        ChatRequest request = new ChatRequest();
        request.setRecipientId(recipient.getId());
        request.setContent("Start a real DM");

        Conversation directConversation = Conversation.builder()
                .isGroup(false)
                .participants(new java.util.ArrayList<>(Arrays.asList(sender, recipient)))
                .build();
        directConversation.setId(UUID.randomUUID());

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(profileRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(conversationRepository.findBetweenUsers(sender.getId(), recipient.getId()))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(directConversation);
        when(messageRepository.save(any(Message.class))).thenReturn(message);
        when(profileService.mapToResponse(any(Profile.class))).thenReturn(new ProfileResponse());

        MessageResponse response = chatService.sendMessage("sender@example.com", request);

        assertNotNull(response);
        verify(conversationRepository).findBetweenUsers(sender.getId(), recipient.getId());
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
    void sendMessage_ExistingConversation_NotParticipant_ShouldThrow() {
        Profile stranger = new Profile();
        stranger.setId(UUID.randomUUID());
        stranger.setUsername("stranger");
        stranger.setEmail("stranger@example.com");

        ChatRequest request = new ChatRequest();
        request.setCid(conversation.getId());
        request.setContent("I should not be here");

        when(profileRepository.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.sendMessage("stranger@example.com", request));

        verify(messageRepository, never()).save(any(Message.class));
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void sendMessage_DirectMessageBlockedWhenRecipientAcceptsNone() {
        ChatRequest request = new ChatRequest();
        request.setRecipientId(recipient.getId());
        request.setContent("Hello");

        UserSettings recipientSettings = UserSettings.builder()
                .profile(recipient)
                .privacySettings(PrivacySettings.builder()
                        .allowMessages("none")
                        .build())
                .build();

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(profileRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(userSettingsRepository.findByProfileId(recipient.getId())).thenReturn(Optional.of(recipientSettings));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> chatService.sendMessage("sender@example.com", request));

        assertEquals("Recipient is not accepting direct messages", exception.getMessage());
        verify(conversationRepository, never()).findBetweenUsers(any(), any());
        verify(messageRepository, never()).save(any(Message.class));
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendMessage_DirectMessageBlockedWhenRecipientAcceptsFriendsAndSenderIsStranger() {
        ChatRequest request = new ChatRequest();
        request.setRecipientId(recipient.getId());
        request.setContent("Hello");

        UserSettings recipientSettings = UserSettings.builder()
                .profile(recipient)
                .privacySettings(PrivacySettings.builder()
                        .allowMessages("friends")
                        .build())
                .build();

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(profileRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(userSettingsRepository.findByProfileId(recipient.getId())).thenReturn(Optional.of(recipientSettings));
        when(friendRepository.areFriends(sender.getId(), recipient.getId())).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> chatService.sendMessage("sender@example.com", request));

        assertEquals("Recipient only accepts messages from friends", exception.getMessage());
        verify(conversationRepository, never()).findBetweenUsers(any(), any());
        verify(messageRepository, never()).save(any(Message.class));
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendMessage_DirectMessageAllowedWhenRecipientAcceptsFriendsAndSenderIsFriend() {
        ChatRequest request = new ChatRequest();
        request.setRecipientId(recipient.getId());
        request.setContent("Hello");

        UserSettings recipientSettings = UserSettings.builder()
                .profile(recipient)
                .privacySettings(PrivacySettings.builder()
                        .allowMessages("friends")
                        .build())
                .build();

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(profileRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(userSettingsRepository.findByProfileId(recipient.getId())).thenReturn(Optional.of(recipientSettings));
        when(friendRepository.areFriends(sender.getId(), recipient.getId())).thenReturn(true);
        when(conversationRepository.findBetweenUsers(sender.getId(), recipient.getId()))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
        when(messageRepository.save(any(Message.class))).thenReturn(message);
        when(profileService.mapToResponse(any(Profile.class))).thenReturn(new ProfileResponse());

        MessageResponse response = chatService.sendMessage("sender@example.com", request);

        assertNotNull(response);
        verify(messageRepository).save(any(Message.class));
        verify(notificationService).createNotification(
                eq(recipient.getId()),
                eq(sender.getId()),
                eq(com.example.demo.enums.NotificationType.MESSAGE),
                any(),
                any(),
                any());
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
    // validateParticipant
    // -------------------------------------------------------------------------

    @Test
    void validateParticipant_Participant_DoesNotThrow() {
        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertDoesNotThrow(() -> chatService.validateParticipant(conversation.getId(), "sender@example.com"));
    }

    @Test
    void validateParticipant_NonParticipant_ThrowsException() {
        Profile stranger = new Profile();
        stranger.setId(UUID.randomUUID());
        stranger.setEmail("stranger@example.com");

        when(profileRepository.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.validateParticipant(conversation.getId(), "stranger@example.com"));
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

    // -------------------------------------------------------------------------
    // deleteMessage
    // -------------------------------------------------------------------------

    @Test
    void deleteMessage_MessageNotInConversation_ShouldThrow() {
        UUID wrongConversationId = UUID.randomUUID();

        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.deleteMessage(wrongConversationId, message.getId(), "sender@example.com"));

        verify(messageRepository, never()).delete(any(Message.class));
    }

    // -------------------------------------------------------------------------
    // createGroupConversation
    // -------------------------------------------------------------------------

    @Test
    void createGroupConversation_Success() {
        Profile member1 = new Profile();
        member1.setId(UUID.randomUUID());
        Profile member2 = new Profile();
        member2.setId(UUID.randomUUID());

        GroupCreateRequest request = GroupCreateRequest.builder()
                .groupName("Test Group")
                .participantIds(Arrays.asList(member1.getId(), member2.getId()))
                .build();

        Conversation groupConv = Conversation.builder()
                .isGroup(true)
                .groupName("Test Group")
                .participants(Arrays.asList(sender, member1, member2))
                .build();
        groupConv.setId(UUID.randomUUID());

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(profileRepository.findById(member1.getId())).thenReturn(Optional.of(member1));
        when(profileRepository.findById(member2.getId())).thenReturn(Optional.of(member2));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(groupConv);
        when(profileService.mapToResponse(any(Profile.class))).thenReturn(new ProfileResponse());

        ConversationResponse response = chatService.createGroupConversation("sender@example.com", request);

        assertNotNull(response);
        assertTrue(response.isGroup());
        assertEquals("Test Group", response.getGroupName());
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void createGroupConversation_TooFewParticipants_ThrowsException() {
        GroupCreateRequest request = GroupCreateRequest.builder()
                .groupName("Solo")
                .participantIds(Collections.singletonList(UUID.randomUUID()))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> chatService.createGroupConversation("sender@example.com", request));

        verifyNoInteractions(profileRepository, conversationRepository);
    }

    @Test
    void createGroupConversation_BlankName_ThrowsException() {
        GroupCreateRequest request = GroupCreateRequest.builder()
                .groupName("   ")
                .participantIds(Arrays.asList(UUID.randomUUID(), UUID.randomUUID()))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> chatService.createGroupConversation("sender@example.com", request));

        verifyNoInteractions(profileRepository, conversationRepository);
    }

    @Test
    void createGroupConversation_ParticipantNotFound_ThrowsException() {
        UUID unknownId = UUID.randomUUID();
        GroupCreateRequest request = GroupCreateRequest.builder()
                .groupName("Test Group")
                .participantIds(Arrays.asList(unknownId, UUID.randomUUID()))
                .build();

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(profileRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> chatService.createGroupConversation("sender@example.com", request));
    }

    // -------------------------------------------------------------------------
    // addParticipant
    // -------------------------------------------------------------------------

    @Test
    void addParticipant_Success() {
        Conversation groupConv = Conversation.builder()
                .isGroup(true)
                .groupName("Group")
                .participants(new java.util.ArrayList<>(Arrays.asList(sender, recipient)))
                .build();
        groupConv.setId(UUID.randomUUID());

        Profile newMember = new Profile();
        newMember.setId(UUID.randomUUID());
        newMember.setEmail("new@example.com");

        when(conversationRepository.findById(groupConv.getId())).thenReturn(Optional.of(groupConv));
        when(profileRepository.findById(newMember.getId())).thenReturn(Optional.of(newMember));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(groupConv);
        when(profileService.mapToResponse(any(Profile.class))).thenReturn(new ProfileResponse());

        ConversationResponse response = chatService.addParticipant(
                groupConv.getId(), newMember.getId(), "sender@example.com");

        assertNotNull(response);
        verify(conversationRepository).save(groupConv);
    }

    @Test
    void addParticipant_NotGroupConversation_ThrowsException() {
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.addParticipant(
                        conversation.getId(), UUID.randomUUID(), "sender@example.com"));
    }

    @Test
    void addParticipant_RequesterNotParticipant_ThrowsException() {
        Conversation groupConv = Conversation.builder()
                .isGroup(true)
                .participants(new java.util.ArrayList<>(Arrays.asList(sender, recipient)))
                .build();
        groupConv.setId(UUID.randomUUID());

        when(conversationRepository.findById(groupConv.getId())).thenReturn(Optional.of(groupConv));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.addParticipant(
                        groupConv.getId(), UUID.randomUUID(), "outsider@example.com"));
    }

    @Test
    void addParticipant_AlreadyMember_ThrowsException() {
        Conversation groupConv = Conversation.builder()
                .isGroup(true)
                .participants(new java.util.ArrayList<>(Arrays.asList(sender, recipient)))
                .build();
        groupConv.setId(UUID.randomUUID());

        when(conversationRepository.findById(groupConv.getId())).thenReturn(Optional.of(groupConv));
        when(profileRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.addParticipant(
                        groupConv.getId(), recipient.getId(), "sender@example.com"));
    }

    // -------------------------------------------------------------------------
    // removeParticipant
    // -------------------------------------------------------------------------

    @Test
    void removeParticipant_Leave_Success() {
        Conversation groupConv = Conversation.builder()
                .isGroup(true)
                .participants(new java.util.ArrayList<>(Arrays.asList(sender, recipient)))
                .build();
        groupConv.setId(UUID.randomUUID());

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(conversationRepository.findById(groupConv.getId())).thenReturn(Optional.of(groupConv));

        chatService.removeParticipant(groupConv.getId(), sender.getId(), "sender@example.com");

        verify(conversationRepository).save(groupConv);
    }

    @Test
    void removeParticipant_NotGroupConversation_ThrowsException() {
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.removeParticipant(
                        conversation.getId(), recipient.getId(), "sender@example.com"));
    }

    @Test
    void removeParticipant_TargetNotInGroup_ThrowsException() {
        Profile outsider = new Profile();
        outsider.setId(UUID.randomUUID());

        Conversation groupConv = Conversation.builder()
                .isGroup(true)
                .participants(new java.util.ArrayList<>(Arrays.asList(sender, recipient)))
                .build();
        groupConv.setId(UUID.randomUUID());

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(conversationRepository.findById(groupConv.getId())).thenReturn(Optional.of(groupConv));

        assertThrows(ResourceNotFoundException.class,
                () -> chatService.removeParticipant(
                        groupConv.getId(), outsider.getId(), "sender@example.com"));
    }

    // -------------------------------------------------------------------------
    // updateGroup
    // -------------------------------------------------------------------------

    @Test
    void updateGroup_Success() {
        Conversation groupConv = Conversation.builder()
                .isGroup(true)
                .groupName("Old Name")
                .participants(new java.util.ArrayList<>(Arrays.asList(sender, recipient)))
                .build();
        groupConv.setId(UUID.randomUUID());

        GroupUpdateRequest request = GroupUpdateRequest.builder()
                .groupName("New Name")
                .build();

        when(conversationRepository.findById(groupConv.getId())).thenReturn(Optional.of(groupConv));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(groupConv);
        when(profileService.mapToResponse(any(Profile.class))).thenReturn(new ProfileResponse());

        ConversationResponse response = chatService.updateGroup(
                groupConv.getId(), request, "sender@example.com");

        assertNotNull(response);
        verify(conversationRepository).save(groupConv);
        assertEquals("New Name", groupConv.getGroupName());
    }

    @Test
    void updateGroup_NotGroupConversation_ThrowsException() {
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.updateGroup(
                        conversation.getId(), new GroupUpdateRequest(), "sender@example.com"));
    }

    @Test
    void updateGroup_RequesterNotParticipant_ThrowsException() {
        Conversation groupConv = Conversation.builder()
                .isGroup(true)
                .participants(new java.util.ArrayList<>(Arrays.asList(sender, recipient)))
                .build();
        groupConv.setId(UUID.randomUUID());

        when(conversationRepository.findById(groupConv.getId())).thenReturn(Optional.of(groupConv));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.updateGroup(
                        groupConv.getId(), new GroupUpdateRequest(), "outsider@example.com"));
    }
}
