package com.example.demo.security;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserDetailsService userDetailsService;

    @Test
    void preSendAuthenticatesConnectFrameFromBearerToken() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService);
        UserDetails userDetails = new User("sender@example.com", "password", Collections.emptyList());
        Message<byte[]> message = connectMessage("Bearer valid-token");

        when(jwtUtils.getUsernameFromToken("valid-token")).thenReturn("sender@example.com");
        when(userDetailsService.loadUserByUsername("sender@example.com")).thenReturn(userDetails);
        when(jwtUtils.validateToken("valid-token", "sender@example.com")).thenReturn(true);

        Message<?> result = interceptor.preSend(message, null);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(accessor.getUser().getName()).isEqualTo("sender@example.com");
    }

    @Test
    void preSendLeavesConnectFrameUnauthenticatedWithoutBearerToken() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService);
        Message<byte[]> message = connectMessage(null);

        Message<?> result = interceptor.preSend(message, null);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isNull();
        verify(jwtUtils, never()).getUsernameFromToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void preSendPreservesUserOnTypingSendFrame() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("sender@example.com", null);
        authentication.setAuthenticated(true);
        Message<byte[]> message = sendMessage("/app/chat.typing", authentication);

        Message<?> result = interceptor.preSend(message, null);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isSameAs(authentication);
        assertThat(accessor.getDestination()).isEqualTo("/app/chat.typing");
    }

    private Message<byte[]> connectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> sendMessage(String destination, java.security.Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setUser(user);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
