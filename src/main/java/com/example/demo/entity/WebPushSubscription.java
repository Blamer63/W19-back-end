package com.example.demo.entity;

import com.example.demo.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_subscription_endpoint", columnNames = "endpoint"),
        indexes = @Index(name = "idx_push_subscription_profile", columnList = "profile_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WebPushSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(nullable = false, length = 2048)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 512)
    private String p256dh;

    @Column(name = "auth_secret", nullable = false, length = 256)
    private String auth;

    @Column(name = "expiration_time")
    private Long expirationTime;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_failure_at")
    private LocalDateTime lastFailureAt;

    @Column(name = "failure_count")
    @Builder.Default
    private int failureCount = 0;
}
