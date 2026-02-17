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
public class PrivacySettingsDto {
    @JsonProperty("show_activity")
    private Boolean showActivity;

    @JsonProperty("show_saved_words")
    private Boolean showSavedWords;
}
