package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationCenterSummaryResponse {
    @JsonProperty("unread_notifications")
    private long unreadNotifications;

    private long total;
}
