package com.example.demo.dto;

import com.example.demo.enums.ScannerTranslationSource;
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
public class DetectedObjectDTO {

    private UUID id;

    private String label;

    private double confidence;

    @JsonProperty("native_word")
    private String nativeWord;

    @JsonProperty("learning_word")
    private String learningWord;

    @JsonProperty("language_code")
    private String languageCode;

    @JsonProperty("translation_source")
    private ScannerTranslationSource translationSource;

    private BoundingBoxDTO box;
}
