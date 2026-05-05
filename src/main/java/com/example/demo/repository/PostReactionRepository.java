package com.example.demo.repository;

import com.example.demo.entity.PostReaction;
import com.example.demo.entity.PostReaction.PostReactionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostReactionRepository extends JpaRepository<PostReaction, PostReactionId> {
    Optional<PostReaction> findByPostIdAndProfileId(UUID postId, UUID profileId);

    long countByPostId(UUID postId);
}
