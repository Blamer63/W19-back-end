package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartSessionRequest {
    @NotNull
    @JsonProperty("session_size")
    private Integer sessionSize; // 5, 10, or 15

    @JsonProperty("language_code")
    private String languageCode;
}
