package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileResponse {
    private java.util.UUID id;
    private String username;
    private String email;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String bio;
    private Double latitude;
    private Double longitude;
    private String location;

    @JsonProperty("created_at")
    private java.time.Instant createdAt;

    private List<UserLanguageDTO> languages;
    private List<String> roles;

    @JsonProperty("followers_count")
    private long followersCount;

    @JsonProperty("following_count")
    private long followingCount;

    @JsonProperty("posts_count")
    private long postsCount;

}
