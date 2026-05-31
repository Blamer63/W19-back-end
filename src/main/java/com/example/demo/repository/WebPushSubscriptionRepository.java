package com.example.demo.repository;

import com.example.demo.entity.WebPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, UUID> {
    List<WebPushSubscription> findByProfileId(UUID profileId);

    Optional<WebPushSubscription> findByEndpoint(String endpoint);

    Optional<WebPushSubscription> findByEndpointAndProfileId(String endpoint, UUID profileId);

    void deleteByEndpoint(String endpoint);

    void deleteByEndpointAndProfileId(String endpoint, UUID profileId);
}
