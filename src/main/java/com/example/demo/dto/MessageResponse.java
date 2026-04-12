package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private UUID id;
    private UUID conversationId;
    private ProfileResponse sender;
    private String content;

    @com.fasterxml.jackson.annotation.JsonProperty("image_url")
    private String imageUrl;

    private LocalDateTime createdAt;
    private boolean isRead;
}
