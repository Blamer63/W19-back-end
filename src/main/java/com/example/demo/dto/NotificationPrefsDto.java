package com.example.demo.dto;

import com.example.demo.entity.NotificationPrefs;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPrefsDto {
    @JsonProperty("push_enabled")
    private Boolean pushEnabled;

    @JsonProperty("email_enabled")
    private Boolean emailEnabled;

    @JsonProperty("like_notifications")
    private Boolean likeNotifications;

    @JsonProperty("comment_notifications")
    private Boolean commentNotifications;

    @JsonProperty("meetup_notifications")
    private Boolean meetupNotifications;

    public static NotificationPrefsDto fromEntity(NotificationPrefs prefs) {
        NotificationPrefs source = prefs != null ? prefs : new NotificationPrefs();
        return NotificationPrefsDto.builder()
                .pushEnabled(source.isPushEnabled())
                .emailEnabled(source.isEmailEnabled())
                .likeNotifications(source.isLikeNotifications())
                .commentNotifications(source.isCommentNotifications())
                .meetupNotifications(source.isMeetupNotifications())
                .build();
    }
}
