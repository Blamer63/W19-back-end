package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteSessionResponse {
    @JsonProperty("session_id")
    private UUID sessionId;

    @JsonProperty("words_practiced")
    private Integer wordsPracticed;

    @JsonProperty("correct_count")
    private Integer correctCount;

    private Integer accuracy;

    @JsonProperty("duration_seconds")
    private Integer durationSeconds;

    private List<WordResultDTO> results;
}
