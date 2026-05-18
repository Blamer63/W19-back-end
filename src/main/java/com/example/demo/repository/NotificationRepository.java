package com.example.demo.repository;

import com.example.demo.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    long countByRecipientId(UUID recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :readAt, n.updatedAt = :updatedAt " +
            "WHERE n.recipient.id = :recipientId AND n.readAt IS NULL")
    int markAllRead(
            @Param("recipientId") UUID recipientId,
            @Param("readAt") LocalDateTime readAt,
            @Param("updatedAt") LocalDateTime updatedAt);
}
