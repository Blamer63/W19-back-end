package com.example.demo.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {
    private String content;
    @com.fasterxml.jackson.annotation.JsonProperty("original_language")
    private String originalLanguage;
    private Double latitude;
    private Double longitude;
}
