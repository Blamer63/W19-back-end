package com.example.demo.service;

import com.example.demo.config.WebPushProperties;
import com.example.demo.entity.NotificationPrefs;
import com.example.demo.entity.UserSettings;
import com.example.demo.entity.WebPushSubscription;
import com.example.demo.enums.NotificationType;
import com.example.demo.repository.UserSettingsRepository;
import com.example.demo.repository.WebPushSubscriptionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebPushNotificationService {

    private static final int HTTP_GONE = 410;
    private static final int HTTP_NOT_FOUND = 404;

    private final WebPushProperties webPushProperties;
    private final WebPushSubscriptionRepository webPushSubscriptionRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void sendNotification(
            UUID recipientId,
            UUID notificationId,
            NotificationType type,
            String title,
            String body,
            String targetUrl) {
        if (!webPushProperties.isConfigured() || !isPushEnabled(recipientId)) {
            return;
        }

        List<WebPushSubscription> subscriptions = webPushSubscriptionRepository.findByProfileId(recipientId);
        if (subscriptions.isEmpty()) {
            return;
        }

        PushService pushService;
        try {
            addBouncyCastleProvider();
            pushService = new PushService(
                    webPushProperties.getVapidPublicKey(),
                    webPushProperties.getVapidPrivateKey(),
                    webPushProperties.getVapidSubject());
        } catch (GeneralSecurityException e) {
            log.warn("Could not initialize Web Push service", e);
            return;
        }

        String payload = buildPayload(notificationId, type, title, body, targetUrl);
        for (WebPushSubscription subscription : subscriptions) {
            sendToSubscription(pushService, subscription, payload);
        }
    }

    private boolean isPushEnabled(UUID recipientId) {
        NotificationPrefs prefs = userSettingsRepository.findByProfileId(recipientId)
                .map(UserSettings::getNotificationPrefs)
                .orElseGet(NotificationPrefs::new);
        return prefs.isPushEnabled();
    }

    private void sendToSubscription(
            PushService pushService,
            WebPushSubscription subscription,
            String payload) {
        try {
            nl.martijndwars.webpush.Notification webPushNotification =
                    new nl.martijndwars.webpush.Notification(
                            subscription.getEndpoint(),
                            subscription.getP256dh(),
                            subscription.getAuth(),
                            payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            webPushProperties.getTtlSeconds());

            HttpResponse response = pushService.send(webPushNotification);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HTTP_GONE || statusCode == HTTP_NOT_FOUND) {
                webPushSubscriptionRepository.delete(subscription);
                return;
            }
            if (statusCode >= 200 && statusCode < 300) {
                subscription.setFailureCount(0);
                subscription.setLastSuccessAt(LocalDateTime.now());
                subscription.setLastFailureAt(null);
            } else {
                markFailure(subscription);
                log.warn("Web Push send failed with status {} for subscription {}", statusCode, subscription.getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailure(subscription);
        } catch (GeneralSecurityException | IOException | JoseException | ExecutionException e) {
            markFailure(subscription);
            log.warn("Web Push send failed for subscription {}", subscription.getId(), e);
        }
    }

    private String buildPayload(
            UUID notificationId,
            NotificationType type,
            String title,
            String body,
            String targetUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", notificationId);
        payload.put("type", type.name());
        payload.put("title", title);
        payload.put("body", body);
        payload.put("targetUrl", targetUrl != null ? targetUrl : "/notifications");
        payload.put("icon", "/pwa-192x192.png");
        payload.put("badge", "/pwa-64x64.png");

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"title\":\"Locale\",\"body\":\"You have a new notification\",\"targetUrl\":\"/notifications\"}";
        }
    }

    private void markFailure(WebPushSubscription subscription) {
        subscription.setFailureCount(subscription.getFailureCount() + 1);
        subscription.setLastFailureAt(LocalDateTime.now());
    }

    private void addBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
