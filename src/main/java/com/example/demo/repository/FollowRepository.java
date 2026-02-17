package com.example.demo.repository;

import com.example.demo.entity.UserFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FollowRepository extends JpaRepository<UserFollow, UUID> {
    long countByFollowerId(UUID followerId);

    long countByFollowingId(UUID followingId);

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    void deleteByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    // Pagination support for followers/following lists
    Page<UserFollow> findByFollowingId(UUID followingId, Pageable pageable);

    Page<UserFollow> findByFollowerId(UUID followerId, Pageable pageable);
}
