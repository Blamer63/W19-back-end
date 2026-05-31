package com.example.demo.security;

import com.example.demo.repository.ConversationRepository;
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

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern CONVERSATION_DESTINATION =
            Pattern.compile("^/topic/conversation\\.([^.]+)(\\.typing)?$");

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final ConversationRepository conversationRepository;

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

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            if (!authenticate(accessor)) {
                return null;
            }
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            if (!authorizeSubscription(accessor)) {
                return null;
            }
        } else if (StompCommand.SEND.equals(command)
                && "/app/chat.typing".equals(accessor.getDestination())) {
            log.debug("STOMP typing SEND: destination={}, user={}",
                    accessor.getDestination(), describeUser(accessor.getUser()));
        }

        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private boolean authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("STOMP CONNECT rejected: missing or malformed Authorization header");
            return false;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        String email;
        try {
            email = jwtUtils.getUsernameFromToken(token);
        } catch (Exception e) {
            log.warn("STOMP CONNECT rejected: JWT parsing failed — {}", e.getMessage());
            return false;
        }

        if (email == null) {
            log.warn("STOMP CONNECT rejected: JWT subject missing");
            return false;
        }

        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(email);
        } catch (Exception e) {
            log.warn("STOMP CONNECT rejected: user not found — {}", email);
            return false;
        }

        if (!jwtUtils.validateToken(token, userDetails.getUsername())) {
            log.warn("STOMP CONNECT rejected: invalid or expired token for {}", email);
            return false;
        }

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()));
        log.info("STOMP CONNECT authenticated as {}", email);
        return true;
    }

    private boolean authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return true;

        Matcher matcher = CONVERSATION_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            // Not a conversation topic (e.g. /user/queue/conversations) — allow
            return true;
        }

        String uuidStr = matcher.group(1);
        UUID conversationId;
        try {
            conversationId = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            log.warn("STOMP SUBSCRIBE rejected: invalid UUID in destination {}", destination);
            return false;
        }

        if (accessor.getUser() == null) {
            log.warn("STOMP SUBSCRIBE rejected: unauthenticated for destination {}", destination);
            return false;
        }

        String email = accessor.getUser().getName();
        boolean isMember = conversationRepository.countParticipantsByEmail(conversationId, email) > 0;
        if (!isMember) {
            log.warn("STOMP SUBSCRIBE rejected: {} is not a participant in {}", email, conversationId);
            return false;
        }

        log.debug("STOMP SUBSCRIBE authorized: {} → {}", email, destination);
        return true;
    }

    private String describeUser(java.security.Principal user) {
        if (user == null) return "null";
        return user.getClass().getSimpleName() + "{name=" + user.getName() + "}";
    }
}
