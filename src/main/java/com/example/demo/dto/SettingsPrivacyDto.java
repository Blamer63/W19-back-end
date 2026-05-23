package com.example.demo.dto;

import com.example.demo.entity.PrivacySettings;
import com.example.demo.enums.LocationVisibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsPrivacyDto {
    @JsonProperty("location_visibility")
    private LocationVisibility locationVisibility;

    @JsonProperty("allow_messages")
    private String allowMessages;

    public static SettingsPrivacyDto fromEntity(PrivacySettings settings) {
        PrivacySettings source = settings != null ? settings : new PrivacySettings();
        return SettingsPrivacyDto.builder()
                .locationVisibility(source.getLocationVisibility())
                .allowMessages(source.getAllowMessages())
                .build();
    }
}
