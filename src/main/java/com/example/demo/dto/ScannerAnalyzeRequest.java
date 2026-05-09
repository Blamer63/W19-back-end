package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScannerAnalyzeRequest {

    @NotBlank
    @JsonProperty("image")
    private String imageBase64;

    @NotBlank
    @JsonProperty("target_language")
    private String targetLanguage;

    @JsonProperty("confidence_threshold")
    private Double confidenceThreshold;

    @JsonProperty("max_results")
    private Integer maxResults;
}
