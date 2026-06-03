package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UpdateWordRequest {
    private String translation;

    @JsonProperty("context")
    private String sourceContext;

    private String topic;
}
