package com.example.demo.dto;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {
    private UUID id;
    private AuthorDto author;
    private String content;

    @com.fasterxml.jackson.annotation.JsonProperty("created_at")
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorDto {
        private UUID id;
        private String username;

        @com.fasterxml.jackson.annotation.JsonProperty("display_name")
        private String displayName;

        @com.fasterxml.jackson.annotation.JsonProperty("avatar_url")
        private String avatarUrl;
    }
}
