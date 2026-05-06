package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeWordDTO {
    private UUID id;
    private String word;
    private String translation;

    @JsonProperty("language_code")
    private String languageCode;

    @JsonProperty("language_flag")
    private String languageFlag;

    @JsonProperty("mastery_level")
    private Integer masteryLevel;
}
