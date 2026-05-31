package com.example.demo.controller;

import com.example.demo.dto.PushSubscriptionDeleteRequest;
import com.example.demo.dto.PushSubscriptionRequest;
import com.example.demo.dto.PushSubscriptionResponse;
import com.example.demo.dto.PushTestRequest;
import com.example.demo.dto.PushVapidPublicKeyResponse;
import com.example.demo.enums.NotificationType;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.service.WebPushNotificationService;
import com.example.demo.service.WebPushSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final WebPushSubscriptionService webPushSubscriptionService;
    private final WebPushNotificationService webPushNotificationService;
    private final ProfileRepository profileRepository;

    @GetMapping("/vapid-public-key")
    public ResponseEntity<PushVapidPublicKeyResponse> getVapidPublicKey() {
        return ResponseEntity.ok(webPushSubscriptionService.getPublicKey());
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<PushSubscriptionResponse> saveSubscription(
            Authentication authentication,
            @Valid @RequestBody PushSubscriptionRequest request) {
        return ResponseEntity.ok(webPushSubscriptionService.saveSubscription(authentication.getName(), request));
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> deleteSubscription(
            Authentication authentication,
            @Valid @RequestBody PushSubscriptionDeleteRequest request) {
        webPushSubscriptionService.deleteSubscription(authentication.getName(), request.getEndpoint());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ResponseEntity<Void> sendTestPush(
            Authentication authentication,
            @RequestBody(required = false) PushTestRequest request) {
        PushVapidPublicKeyResponse key = webPushSubscriptionService.getPublicKey();
        if (!key.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Web push is not configured");
        }

        var profile = profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String title = request != null && request.getTitle() != null ? request.getTitle() : "Locale notifications are on";
        String body = request != null && request.getBody() != null ? request.getBody() : "You will receive Locale activity here.";

        webPushNotificationService.sendNotification(
                profile.getId(),
                java.util.UUID.randomUUID(),
                NotificationType.MESSAGE,
                title,
                body,
                "/notifications");
        return ResponseEntity.accepted().build();
    }
}
