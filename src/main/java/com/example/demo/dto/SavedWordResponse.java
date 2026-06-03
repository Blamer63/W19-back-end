package com.example.demo.dto;

import com.example.demo.enums.SourceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedWordResponse {
    private UUID id;
    private String word;
    private String translation;

    @JsonProperty("language_code")
    private String languageCode;

    @JsonProperty("language_name")
    private String languageName;

    @JsonProperty("language_flag")
    private String languageFlag;

    private SourceType source;

    @JsonProperty("source_id")
    private UUID sourceId;

    @JsonProperty("context")
    private String sourceContext;

    private String topic;

    @JsonProperty("mastery_level")
    private Integer masteryLevel;

    @JsonProperty("next_review")
    private Instant nextReview;

    @JsonProperty("created_at")
    private Instant createdAt;
}
