package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {

    private UUID id;

    private String username;

    private String email;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String bio;

    private String location;

    private List<String> roles;

    private List<AdminUserLanguageResponse> languages;

    @JsonProperty("posts_count")
    private long postsCount;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    /** Starter-seed run status for this user; null when no run record exists. */
    @JsonProperty("seed_status")
    private AdminSeedRunResponse seedStatus;
}
