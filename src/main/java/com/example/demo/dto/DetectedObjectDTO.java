package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single detected object from the vision-service /analyze response.
 *
 * canonical_label — English taxonomy key (stable vocabulary ID, used for saving)
 * translated_label — localized word from the static JSON translation store
 *
 * Both fields are always populated. If a translation is missing, translated_label
 * falls back to the English canonical label (handled inside translation_store.py).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DetectedObjectDTO {

    @JsonProperty("canonical_label")
    private String canonicalLabel;

    @JsonProperty("translated_label")
    private String translatedLabel;
}
