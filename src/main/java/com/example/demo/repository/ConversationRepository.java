package com.example.demo.repository;

import com.example.demo.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("SELECT c FROM Conversation c JOIN c.participants p WHERE p.id = :profileId ORDER BY c.lastMessageAt DESC")
    Page<Conversation> findByParticipantId(@Param("profileId") UUID profileId, Pageable pageable);

    @Query("SELECT c FROM Conversation c JOIN c.participants p1 JOIN c.participants p2 WHERE p1.id = :id1 AND p2.id = :id2")
    Optional<Conversation> findBetweenUsers(@Param("id1") UUID id1, @Param("id2") UUID id2);
}
