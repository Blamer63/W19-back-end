package com.example.demo.controller;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.MessageResponse;
import com.example.demo.dto.ProfileResponse;
import com.example.demo.entity.Profile;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.service.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerWebSocketTest {

    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ProfileRepository profileRepository;

    @Test
    void processMessageBroadcastsSavedMessageToConversationTopic() {
        ChatController controller = new ChatController(chatService, messagingTemplate, profileRepository);
        UUID conversationId = UUID.randomUUID();
        ChatRequest request = new ChatRequest();
        request.setCid(conversationId);
        request.setContent("hello");
        MessageResponse response = MessageResponse.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .sender(ProfileResponse.builder().email("sender@example.com").build())
                .content("hello")
                .build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("sender@example.com", null);
        authentication.setAuthenticated(true);

        when(chatService.sendMessage("sender@example.com", request)).thenReturn(response);

        controller.processMessage(request, authentication);

        verify(messagingTemplate).convertAndSend("/topic/conversation." + conversationId, response);
    }

    @Test
    void processTypingValidatesParticipantAndBroadcastsSenderIdentity() {
        ChatController controller = new ChatController(chatService, messagingTemplate, profileRepository);
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("sender@example.com", null);
        authentication.setAuthenticated(true);
        Profile sender = Profile.builder()
                .email("sender@example.com")
                .displayName("Sender")
                .build();
        sender.setId(senderId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("cid", conversationId.toString());
        payload.put("isTyping", true);

        when(profileRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));

        controller.processTyping(payload, authentication);

        verify(chatService).validateParticipant(conversationId, "sender@example.com");
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversation." + conversationId + ".typing"),
                payloadCaptor.capture());

        Map<String, Object> enriched = payloadCaptor.getValue();
        assertThat(enriched).containsEntry("cid", conversationId.toString());
        assertThat(enriched).containsEntry("isTyping", true);
        assertThat(enriched).containsEntry("userId", senderId.toString());
        assertThat(enriched).containsEntry("displayName", "Sender");
    }

    @Test
    void processTypingRejectsMissingAuthenticationBeforeBroadcast() {
        ChatController controller = new ChatController(chatService, messagingTemplate, profileRepository);
        Map<String, Object> payload = new HashMap<>();
        payload.put("cid", UUID.randomUUID().toString());
        payload.put("isTyping", true);

        assertThatThrownBy(() -> controller.processTyping(payload, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authentication required");

        verify(chatService, never()).validateParticipant(
                org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.anyString());
        verify(messagingTemplate, never()).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object.class));
    }
}
