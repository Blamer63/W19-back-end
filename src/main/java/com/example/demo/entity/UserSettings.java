package com.example.demo.entity;

import com.example.demo.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserSettings extends BaseEntity {

        @OneToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "profile_id", nullable = false)
        private Profile profile;

        @Embedded
        @AttributeOverrides({
                        @AttributeOverride(name = "pushEnabled", column = @Column(name = "notify_push")),
                        @AttributeOverride(name = "emailEnabled", column = @Column(name = "notify_email")),
                        @AttributeOverride(name = "likeNotifications", column = @Column(name = "notify_likes")),
                        @AttributeOverride(name = "commentNotifications", column = @Column(name = "notify_comments")),
                        @AttributeOverride(name = "meetupNotifications", column = @Column(name = "notify_meetups"))
        })
        @Builder.Default
        private NotificationPrefs notificationPrefs = new NotificationPrefs();

        @Embedded
        @AttributeOverrides({
                        @AttributeOverride(name = "locationVisibility", column = @Column(name = "privacy_location_visibility")),
                        @AttributeOverride(name = "allowMessages", column = @Column(name = "privacy_allow_messages"))
        })
        @Builder.Default
        private PrivacySettings privacySettings = new PrivacySettings();

        @Builder.Default
        private String theme = "system";
}
