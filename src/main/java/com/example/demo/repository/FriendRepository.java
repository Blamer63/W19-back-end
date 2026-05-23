package com.example.demo.repository;

import com.example.demo.entity.Friend;
import com.example.demo.enums.FriendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendRepository extends JpaRepository<Friend, UUID> {

    // Find a friend record between two users regardless of who sent it
    @Query("SELECT f FROM Friend f WHERE " +
            "(f.requester.id = :userA AND f.receiver.id = :userB) OR " +
            "(f.requester.id = :userB AND f.receiver.id = :userA)")
    Optional<Friend> findBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);

    // Check if two users are mutual friends (ACCEPTED)
    @Query("SELECT COUNT(f) > 0 FROM Friend f WHERE " +
            "f.status = 'ACCEPTED' AND " +
            "((f.requester.id = :userA AND f.receiver.id = :userB) OR " +
            "(f.requester.id = :userB AND f.receiver.id = :userA))")
    boolean areFriends(@Param("userA") UUID userA, @Param("userB") UUID userB);

    // Get all accepted friends of a user (paginated)
    @Query("SELECT f FROM Friend f WHERE " +
            "f.status = 'ACCEPTED' AND " +
            "(f.requester.id = :userId OR f.receiver.id = :userId)")
    Page<Friend> findAcceptedFriends(@Param("userId") UUID userId, Pageable pageable);

    // Incoming pending requests (someone sent a request TO this user)
    Page<Friend> findByReceiverIdAndStatus(UUID receiverId, FriendStatus status, Pageable pageable);

    long countByReceiverIdAndStatus(UUID receiverId, FriendStatus status);

    // Outgoing pending requests (this user sent a request to someone else)
    Page<Friend> findByRequesterIdAndStatus(UUID requesterId, FriendStatus status, Pageable pageable);

    // Get all accepted friend IDs of a user (used for map filtering)
    @Query("SELECT CASE WHEN f.requester.id = :userId THEN f.receiver.id ELSE f.requester.id END " +
            "FROM Friend f WHERE f.status = 'ACCEPTED' AND " +
            "(f.requester.id = :userId OR f.receiver.id = :userId)")
    java.util.List<UUID> findAcceptedFriendIds(@Param("userId") UUID userId);
}
