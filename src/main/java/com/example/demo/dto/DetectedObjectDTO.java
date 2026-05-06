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
public class DetectedObjectDTO {

    private String label;

    private double confidence;

    @JsonProperty("native_word")
    private String nativeWord;

    @JsonProperty("learning_word")
    private String learningWord;

    @JsonProperty("language_code")
    private String languageCode;
}
