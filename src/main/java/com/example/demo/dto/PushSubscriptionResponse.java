package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PushSubscriptionResponse {
    private UUID id;
    private String endpoint;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
