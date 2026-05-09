package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DetectedObjectResponse {

    private List<String> labels;

    private String description;

    @JsonProperty("translated_labels")
    private List<String> translatedLabels;

}
