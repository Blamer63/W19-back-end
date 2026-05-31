package com.example.demo.dto;

import com.example.demo.enums.SourceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWordRequest {
    @NotBlank
    private String word;

    @NotBlank
    private String translation;

    @NotBlank
    @JsonProperty("language_code")
    private String languageCode;

    @NotNull
    private SourceType source;

    @JsonProperty("source_id")
    private UUID sourceId;

    @JsonProperty("context")
    private String sourceContext;
}
