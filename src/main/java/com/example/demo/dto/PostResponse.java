package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class PostResponse {
    private UUID id;
    private String content;

    @JsonProperty("original_language")
    private String originalLanguage;

    @JsonProperty("image_url")
    private String imageUrl;

    private Double latitude;
    private Double longitude;
    private String location;
    private String distance;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    private String status;
    private AuthorDTO author;
    private ReactionSummaryDTO reactions;

    @JsonProperty("user_reaction")
    private String userReaction;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorDTO {
        private UUID id;
        private String username;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("avatar_url")
        private String avatarUrl;

        private String language;

        @JsonProperty("flag_emoji")
        private String flagEmoji;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionSummaryDTO {
        private int likes;
        private int comments;
    }
}
