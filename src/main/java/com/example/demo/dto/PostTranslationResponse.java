package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostTranslationResponse {
    @com.fasterxml.jackson.annotation.JsonProperty("language_code")
    private String languageCode;

    @com.fasterxml.jackson.annotation.JsonProperty("translated_content")
    private String translatedContent;
}
