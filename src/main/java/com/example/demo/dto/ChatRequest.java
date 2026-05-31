package com.example.demo.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class ChatRequest {
    private UUID cid; // Conversation ID
    private UUID recipientId; // For new conversations
    private String content;
}
