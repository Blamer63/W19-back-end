package com.example.demo.service;

import com.example.demo.dto.NotificationActorResponse;
import com.example.demo.dto.NotificationCenterSummaryResponse;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.entity.Notification;
import com.example.demo.entity.NotificationPrefs;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserSettings;
import com.example.demo.enums.NotificationType;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ProfileRepository profileRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final WebPushNotificationService webPushNotificationService;

    @Transactional
    public Optional<NotificationResponse> createNotification(
            UUID recipientId,
            UUID actorId,
            NotificationType type,
            String title,
            String body,
            String targetUrl) {
        if (actorId != null && actorId.equals(recipientId)) {
            return Optional.empty();
        }

        Profile recipient = profileRepository.findById(recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));

        if (!isEnabledForRecipient(recipient.getId(), type)) {
            return Optional.empty();
        }

        Profile actor = null;
        if (actorId != null) {
            actor = profileRepository.findById(actorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Actor not found"));
        }

        Notification notification = Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .title(title)
                .body(body)
                .targetUrl(targetUrl)
                .build();

        Notification saved = notificationRepository.save(notification);
        webPushNotificationService.sendNotification(
                recipient.getId(),
                saved.getId(),
                saved.getType(),
                saved.getTitle(),
                saved.getBody(),
                saved.getTargetUrl());

        return Optional.of(toResponse(saved));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listNotifications(String email, boolean unreadOnly, Pageable pageable) {
        Profile recipient = getProfileByEmail(email);
        Page<Notification> notifications = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(recipient.getId(), pageable)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipient.getId(), pageable);
        return notifications.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NotificationCenterSummaryResponse getSummary(String email) {
        Profile recipient = getProfileByEmail(email);
        long unread = notificationRepository.countByRecipientIdAndReadAtIsNull(recipient.getId());
        long total = notificationRepository.countByRecipientId(recipient.getId());
        return NotificationCenterSummaryResponse.builder()
                .unreadNotifications(unread)
                .total(total)
                .build();
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId, String email) {
        Profile recipient = getProfileByEmail(email);
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipient.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
        }

        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationCenterSummaryResponse markAllRead(String email) {
        Profile recipient = getProfileByEmail(email);
        LocalDateTime readAt = LocalDateTime.now();
        notificationRepository.markAllRead(recipient.getId(), readAt, readAt);
        long total = notificationRepository.countByRecipientId(recipient.getId());
        return NotificationCenterSummaryResponse.builder()
                .unreadNotifications(0)
                .total(total)
                .build();
    }

    private boolean isEnabledForRecipient(UUID recipientId, NotificationType type) {
        NotificationPrefs prefs = userSettingsRepository.findByProfileId(recipientId)
                .map(UserSettings::getNotificationPrefs)
                .orElseGet(NotificationPrefs::new);

        return switch (type) {
            case POST_REACTION -> prefs.isLikeNotifications();
            case POST_COMMENT -> prefs.isCommentNotifications();
            case MEETUP_JOINED, MEETUP_UPDATED, MEETUP_REMINDER -> prefs.isMeetupNotifications();
            case FRIEND_POST, SAVED_WORD, SCAN_DETECTED_WORD -> prefs.isPushEnabled();
            default -> true;
        };
    }

    private Profile getProfileByEmail(String email) {
        return profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .targetUrl(notification.getTargetUrl())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .actor(toActorResponse(notification.getActor()))
                .build();
    }

    private NotificationActorResponse toActorResponse(Profile actor) {
        if (actor == null) {
            return null;
        }

        return NotificationActorResponse.builder()
                .id(actor.getId())
                .username(actor.getUsername())
                .displayName(actor.getDisplayName())
                .avatarUrl(actor.getAvatarUrl())
                .build();
    }
}
