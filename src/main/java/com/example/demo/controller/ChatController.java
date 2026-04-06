package com.example.demo.controller;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ConversationResponse;
import com.example.demo.dto.MessageResponse;
import com.example.demo.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // WebSocket: Send message
    @MessageMapping("/chat.send")
    public void processMessage(@Payload ChatRequest chatRequest, Authentication authentication) {
        MessageResponse savedMessage = chatService.sendMessage(authentication.getName(), chatRequest);

        // Broadcast to the specific conversation topic
        messagingTemplate.convertAndSend(
                "/topic/conversation." + savedMessage.getConversationId(),
                savedMessage);

        // Also notify the recipient via their private queue if needed
        // For simplicity, we use the conversation topic
    }

    // WebSocket: Typing status
    @MessageMapping("/chat.typing")
    public void processTyping(@Payload java.util.Map<String, Object> payload, Authentication authentication) {
        Object cid = payload.get("cid");
        if (cid != null) {
            messagingTemplate.convertAndSend(
                    "/topic/conversation." + cid + ".typing",
                    payload);
        }
    }

    // REST: List conversations
    @GetMapping
    public ResponseEntity<Page<ConversationResponse>> getConversations(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(chatService.getUserConversations(authentication.getName(), pageable));
    }

    // REST: Start new conversation
    @PostMapping
    public ResponseEntity<MessageResponse> startConversation(
            @RequestBody ChatRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.sendMessage(authentication.getName(), request));
    }

    // REST: Get message history
    @GetMapping("/{id}/messages")
    public ResponseEntity<Page<MessageResponse>> getMessages(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(chatService.getConversationMessages(id, pageable));
    }

    // REST: Fallback send message
    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID id,
            @RequestBody ChatRequest request,
            Authentication authentication) {
        request.setCid(id);
        MessageResponse response = chatService.sendMessage(authentication.getName(), request);

        // Still broadcast via WS so live clients get it
        messagingTemplate.convertAndSend("/topic/conversation." + id, response);

        return ResponseEntity.ok(response);
    }

    // REST: Mark as read
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id, Authentication authentication) {
        chatService.markAsRead(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    // REST: Delete message (also removes S3 image attachment if present)
    @DeleteMapping("/{id}/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable UUID id,
            @PathVariable UUID messageId,
            Authentication authentication) {
        chatService.deleteMessage(messageId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
