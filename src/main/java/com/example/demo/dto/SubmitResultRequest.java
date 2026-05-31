package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SubmitResultRequest {
    @NotNull
    @JsonProperty("word_id")
    private UUID wordId;

    @NotNull
    @JsonProperty("is_correct")
    private Boolean isCorrect;

    @JsonProperty("response_time_ms")
    private Integer responseTimeMs;
}
