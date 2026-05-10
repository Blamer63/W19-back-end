package com.example.demo.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        MessageHeaderAccessor mutableAccessor = MessageHeaderAccessor.getMutableAccessor(message);
        StompHeaderAccessor accessor;
        if (mutableAccessor instanceof StompHeaderAccessor stompHeaderAccessor) {
            accessor = stompHeaderAccessor;
        } else {
            accessor = StompHeaderAccessor.wrap(message);
        }
        accessor.setLeaveMutable(true);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())
                && "/app/chat.typing".equals(accessor.getDestination())) {
            log.info("STOMP typing SEND received: destination={}, user={}",
                    accessor.getDestination(),
                    describeUser(accessor.getUser()));
        }

        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("STOMP CONNECT missing bearer Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        String email = jwtUtils.getUsernameFromToken(token);
        if (email == null) {
            log.warn("STOMP CONNECT rejected: JWT subject missing");
            return;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!jwtUtils.validateToken(token, userDetails.getUsername())) {
            log.warn("STOMP CONNECT rejected: invalid token for {}", email);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities());
        accessor.setUser(authentication);
        log.info("STOMP CONNECT authenticated as {}", email);
    }

    private String describeUser(java.security.Principal user) {
        if (user == null) {
            return "null";
        }
        return user.getClass().getSimpleName() + "{name=" + user.getName() + "}";
    }
}
