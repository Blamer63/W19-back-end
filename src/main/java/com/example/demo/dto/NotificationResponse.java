package com.example.demo.dto;

import com.example.demo.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private NotificationType type;
    private String title;
    private String body;

    @JsonProperty("target_url")
    private String targetUrl;

    @JsonProperty("read_at")
    private LocalDateTime readAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    private NotificationActorResponse actor;
}
