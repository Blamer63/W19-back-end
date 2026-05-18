package com.example.demo.repository;

import com.example.demo.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    Page<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.conversation.id = :conversationId AND m.isRead = false")
    void markAllAsRead(@Param("conversationId") UUID conversationId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId AND m.isRead = false AND m.sender.id <> :requesterId")
    int countUnread(@Param("conversationId") UUID conversationId, @Param("requesterId") UUID requesterId);

    // Group chats need per-participant read receipts; until then, only DMs can
    // be counted without one participant's read state affecting everyone else.
    @Query("SELECT COUNT(DISTINCT m.id) FROM Message m JOIN m.conversation.participants p " +
            "WHERE p.id = :requesterId AND m.isRead = false AND m.sender.id <> :requesterId " +
            "AND SIZE(m.conversation.participants) = 2")
    long countUnreadForUser(@Param("requesterId") UUID requesterId);
}
