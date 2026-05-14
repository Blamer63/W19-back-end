package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScannerAnalyzeRequest {

    @NotBlank
    @JsonProperty("image")
    private String imageBase64;

    // Accept both "language" (vision-service native field) and
    // "target_language" (legacy Spring Boot field name). Defaults to "en"
    // so the field is never required — removing the 400 rejection on absence.
    @JsonAlias({"language", "target_language"})
    @JsonProperty("target_language")
    private String targetLanguage = "en";

    @JsonProperty("confidence_threshold")
    private Double confidenceThreshold;

    @JsonProperty("max_results")
    private Integer maxResults;
}
