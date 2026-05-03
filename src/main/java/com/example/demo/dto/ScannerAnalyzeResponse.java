package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ScannerAnalyzeResponse {

    private String status;

    private String message;

    @JsonProperty("target_language")
    private String targetLanguage;

    @JsonProperty("detection_count")
    private int detectionCount;

    private List<DetectedObjectResponse> detections;
}
