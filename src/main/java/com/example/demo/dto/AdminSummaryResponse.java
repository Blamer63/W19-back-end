package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSummaryResponse {

    @JsonProperty("generated_at")
    private LocalDateTime generatedAt;

    private List<AdminMetricResponse> metrics;

    @JsonProperty("future_features")
    private List<AdminFeatureStatusResponse> futureFeatures;
}
