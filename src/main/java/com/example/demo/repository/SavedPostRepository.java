package com.example.demo.repository;

import com.example.demo.entity.SavedPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedPostRepository extends JpaRepository<SavedPost, UUID> {
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);
    Optional<SavedPost> findByUserIdAndPostId(UUID userId, UUID postId);
    void deleteByUserIdAndPostId(UUID userId, UUID postId);
    Page<SavedPost> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
