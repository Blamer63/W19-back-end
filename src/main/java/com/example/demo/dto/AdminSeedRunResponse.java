package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSeedRunResponse {

    @JsonProperty("profile_id")
    private UUID profileId;

    private String username;

    private String email;

    @JsonProperty("seed_key")
    private String seedKey;

    /** PENDING, RUNNING, COMPLETED, or null when no seed run record exists. */
    private String status;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("completed_at")
    private Instant completedAt;
}
