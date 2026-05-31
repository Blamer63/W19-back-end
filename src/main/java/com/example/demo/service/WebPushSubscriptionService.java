package com.example.demo.service;

import com.example.demo.config.WebPushProperties;
import com.example.demo.dto.PushSubscriptionRequest;
import com.example.demo.dto.PushSubscriptionResponse;
import com.example.demo.dto.PushVapidPublicKeyResponse;
import com.example.demo.entity.Profile;
import com.example.demo.entity.WebPushSubscription;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.WebPushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WebPushSubscriptionService {

    private final WebPushProperties webPushProperties;
    private final ProfileRepository profileRepository;
    private final WebPushSubscriptionRepository webPushSubscriptionRepository;

    @Transactional(readOnly = true)
    public PushVapidPublicKeyResponse getPublicKey() {
        return PushVapidPublicKeyResponse.builder()
                .enabled(webPushProperties.isConfigured())
                .publicKey(webPushProperties.isConfigured() ? webPushProperties.getVapidPublicKey() : null)
                .build();
    }

    @Transactional
    public PushSubscriptionResponse saveSubscription(String email, PushSubscriptionRequest request) {
        if (!webPushProperties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Web push is not configured");
        }

        Profile profile = getProfileByEmail(email);
        WebPushSubscription subscription = webPushSubscriptionRepository.findByEndpoint(request.getEndpoint())
                .orElseGet(WebPushSubscription::new);

        subscription.setProfile(profile);
        subscription.setEndpoint(request.getEndpoint());
        subscription.setP256dh(request.getKeys().getP256dh());
        subscription.setAuth(request.getKeys().getAuth());
        subscription.setExpirationTime(request.getExpirationTime());
        subscription.setFailureCount(0);
        subscription.setLastFailureAt(null);

        return toResponse(webPushSubscriptionRepository.save(subscription));
    }

    @Transactional
    public void deleteSubscription(String email, String endpoint) {
        Profile profile = getProfileByEmail(email);
        webPushSubscriptionRepository.deleteByEndpointAndProfileId(endpoint, profile.getId());
    }

    private Profile getProfileByEmail(String email) {
        return profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private PushSubscriptionResponse toResponse(WebPushSubscription subscription) {
        return PushSubscriptionResponse.builder()
                .id(subscription.getId())
                .endpoint(subscription.getEndpoint())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
