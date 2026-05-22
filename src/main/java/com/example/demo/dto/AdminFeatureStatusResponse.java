package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFeatureStatusResponse {

    private String key;

    private String label;

    private String status;

    private String description;

    @JsonProperty("enabled")
    private boolean enabled;
}
