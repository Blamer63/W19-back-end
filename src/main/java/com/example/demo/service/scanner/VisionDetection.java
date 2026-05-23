package com.example.demo.service.scanner;

import com.example.demo.dto.BoundingBoxDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisionDetection {

    @JsonProperty("canonical_label")
    private String canonicalLabel;

    @JsonProperty("translated_label")
    private String translatedLabel;

    private double confidence;

    private BoundingBoxDTO box;
}
