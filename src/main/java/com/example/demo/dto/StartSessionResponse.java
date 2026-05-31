package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartSessionResponse {
    @JsonProperty("session_id")
    private UUID sessionId;

    private List<PracticeWordDTO> words;

    @JsonProperty("started_at")
    private Instant startedAt;
}
