package com.example.demo.dto;

import com.example.demo.enums.ScanSessionStatus;
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
public class ScanSessionSummaryResponse {

    private UUID id;

    @JsonProperty("detected_count")
    private int detectedCount;

    private ScanSessionStatus status;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
