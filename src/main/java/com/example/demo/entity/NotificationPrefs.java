package com.example.demo.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPrefs {
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonProperty("push_enabled")
    private boolean pushEnabled = true;

    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonProperty("email_enabled")
    private boolean emailEnabled = false;

    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonProperty("like_notifications")
    private boolean likeNotifications = true;

    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonProperty("comment_notifications")
    private boolean commentNotifications = true;

    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonProperty("meetup_notifications")
    private boolean meetupNotifications = true;
}
