package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextTranslationResponse {
    @JsonProperty("translated_text") private String translatedText;
}
