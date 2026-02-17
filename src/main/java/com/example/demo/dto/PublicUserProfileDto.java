package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserProfileDto {
    private UUID id;
    private String username;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String bio;
    private String location;

    @JsonProperty("created_at")
    private Instant createdAt;

    private List<UserLanguageDTO> languages;

    @JsonProperty("followers_count")
    private Integer followersCount;

    @JsonProperty("following_count")
    private Integer followingCount;

    @JsonProperty("posts_count")
    private Integer postsCount;

    @JsonProperty("is_following")
    private Boolean isFollowing;

    @JsonProperty("privacy_settings")
    private PrivacySettingsDto privacySettings;
}
