package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DetectedObjectResponse {

    private String label;

    @JsonProperty("translated_label")
    private String translatedLabel;

    private String description;

    @JsonProperty("translated_description")
    private String translatedDescription;

    private Double confidence;

    @JsonProperty("translated")
    private boolean translated;
}
