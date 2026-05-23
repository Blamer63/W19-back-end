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
public class UserSettingsDTO {
    @JsonProperty("notification_prefs")
    private NotificationPrefsDto notificationPrefs;

    @JsonProperty("privacy_settings")
    private SettingsPrivacyDto privacySettings;

    private String theme;
}
