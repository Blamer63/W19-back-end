package com.example.demo.dto;

import com.example.demo.entity.NotificationPrefs;
import com.example.demo.entity.PrivacySettings;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSettingsDTO {
    @JsonProperty("notification_prefs")
    private NotificationPrefs notificationPrefs;

    @JsonProperty("privacy_settings")
    private PrivacySettings privacySettings;

    private String theme;
}
