package com.example.demo.security;

import com.example.demo.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private ConversationRepository conversationRepository;

    // ── CONNECT tests ──────────────────────────────────────────────────────────

    @Test
    void preSendAuthenticatesConnectFrameFromBearerToken() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);
        UserDetails userDetails = new User("sender@example.com", "password", Collections.emptyList());

        when(jwtUtils.getUsernameFromToken("valid-token")).thenReturn("sender@example.com");
        when(userDetailsService.loadUserByUsername("sender@example.com")).thenReturn(userDetails);
        when(jwtUtils.validateToken("valid-token", "sender@example.com")).thenReturn(true);

        Message<?> result = interceptor.preSend(connectMessage("Bearer valid-token"), null);

        assertThat(result).isNotNull();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(accessor.getUser().getName()).isEqualTo("sender@example.com");
    }

    @Test
    void preSendRejectsConnectFrameWithoutAuthorizationHeader() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);

        Message<?> result = interceptor.preSend(connectMessage(null), null);

        assertThat(result).isNull();
        verify(jwtUtils, never()).getUsernameFromToken(anyString());
    }

    @Test
    void preSendRejectsConnectFrameWithMalformedAuthorizationHeader() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);

        Message<?> result = interceptor.preSend(connectMessage("Basic dXNlcjpwYXNz"), null);

        assertThat(result).isNull();
        verify(jwtUtils, never()).getUsernameFromToken(anyString());
    }

    @Test
    void preSendRejectsConnectFrameWhenJwtParsingThrows() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);

        when(jwtUtils.getUsernameFromToken("bad-token")).thenThrow(new RuntimeException("malformed jwt"));

        Message<?> result = interceptor.preSend(connectMessage("Bearer bad-token"), null);

        assertThat(result).isNull();
    }

    @Test
    void preSendRejectsConnectFrameWhenTokenInvalid() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);
        UserDetails userDetails = new User("user@example.com", "password", Collections.emptyList());

        when(jwtUtils.getUsernameFromToken("expired-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        when(jwtUtils.validateToken("expired-token", "user@example.com")).thenReturn(false);

        Message<?> result = interceptor.preSend(connectMessage("Bearer expired-token"), null);

        assertThat(result).isNull();
    }

    // ── SUBSCRIBE tests ────────────────────────────────────────────────────────

    @Test
    void preSendAllowsSubscribeToNonConversationDestination() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);

        Message<?> result = interceptor.preSend(subscribeMessage("/user/queue/conversations", authenticatedUser("user@example.com")), null);

        assertThat(result).isNotNull();
    }

    @Test
    void preSendAllowsSubscribeToConversationTopicForParticipant() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);
        UUID conversationId = UUID.randomUUID();

        when(conversationRepository.countParticipantsByEmail(conversationId, "user@example.com")).thenReturn(1L);

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/conversation." + conversationId, authenticatedUser("user@example.com")), null);

        assertThat(result).isNotNull();
    }

    @Test
    void preSendAllowsSubscribeToTypingTopicForParticipant() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);
        UUID conversationId = UUID.randomUUID();

        when(conversationRepository.countParticipantsByEmail(conversationId, "user@example.com")).thenReturn(1L);

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/conversation." + conversationId + ".typing", authenticatedUser("user@example.com")), null);

        assertThat(result).isNotNull();
    }

    @Test
    void preSendRejectsSubscribeToConversationTopicForNonParticipant() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);
        UUID conversationId = UUID.randomUUID();

        when(conversationRepository.countParticipantsByEmail(conversationId, "outsider@example.com")).thenReturn(0L);

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/conversation." + conversationId, authenticatedUser("outsider@example.com")), null);

        assertThat(result).isNull();
    }

    @Test
    void preSendRejectsSubscribeToConversationTopicWhenUnauthenticated() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);
        UUID conversationId = UUID.randomUUID();

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/conversation." + conversationId, null), null);

        assertThat(result).isNull();
    }

    @Test
    void preSendRejectsSubscribeWithInvalidUuidInDestination() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/conversation.not-a-uuid", authenticatedUser("user@example.com")), null);

        assertThat(result).isNull();
    }

    // ── SEND tests ─────────────────────────────────────────────────────────────

    @Test
    void preSendPreservesUserOnTypingSendFrame() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, conversationRepository);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("sender@example.com", null);
        authentication.setAuthenticated(true);

        Message<?> result = interceptor.preSend(sendMessage("/app/chat.typing", authentication), null);

        assertThat(result).isNotNull();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isSameAs(authentication);
        assertThat(accessor.getDestination()).isEqualTo("/app/chat.typing");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Message<byte[]> connectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(String destination, java.security.Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (user != null) accessor.setUser(user);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> sendMessage(String destination, java.security.Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setUser(user);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private java.security.Principal authenticatedUser(String email) {
        return new TestingAuthenticationToken(email, null);
    }
}
