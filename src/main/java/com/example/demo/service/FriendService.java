package com.example.demo.service;

import com.example.demo.dto.FriendRequestResponse;
import com.example.demo.dto.ProfileResponse;
import com.example.demo.entity.Friend;
import com.example.demo.entity.Profile;
import com.example.demo.enums.FriendStatus;
import com.example.demo.enums.NotificationType;
import com.example.demo.repository.FriendRepository;
import com.example.demo.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final ProfileRepository profileRepository;
    private final ProfileService profileService;
    private final NotificationService notificationService;

    @Transactional
    public FriendRequestResponse sendRequest(UUID receiverId, String senderEmail) {
        Profile sender = profileRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (sender.getId().equals(receiverId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot send a friend request to yourself");
        }

        Profile receiver = profileRepository.findById(receiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found"));

        // Check if a record already exists (in either direction)
        friendRepository.findBetween(sender.getId(), receiverId).ifPresent(existing -> {
            if (existing.getStatus() == FriendStatus.ACCEPTED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Already friends");
            }
            if (existing.getStatus() == FriendStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Friend request already sent");
            }
            // If REJECTED, allow re-send by deleting the old record
            if (existing.getStatus() == FriendStatus.REJECTED) {
                friendRepository.delete(existing);
            }
        });

        Friend friend = Friend.builder()
                .requester(sender)
                .receiver(receiver)
                .status(FriendStatus.PENDING)
                .build();

        Friend saved = friendRepository.save(friend);
        notificationService.createNotification(
                receiver.getId(),
                sender.getId(),
                NotificationType.FRIEND_REQUEST,
                "New friend request",
                profileService.displayName(sender) + " sent you a friend request.",
                "/friends/requests");
        return mapToResponse(saved, sender.getId());
    }

    @Transactional
    public FriendRequestResponse respondToRequest(UUID friendId, String action, String responderEmail) {
        Profile responder = profileRepository.findByEmail(responderEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend request not found"));

        // Only the receiver can accept or reject
        if (!friend.getReceiver().getId().equals(responder.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to respond to this request");
        }

        if (friend.getStatus() != FriendStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is no longer pending");
        }

        if ("accept".equalsIgnoreCase(action)) {
            friend.setStatus(FriendStatus.ACCEPTED);
        } else if ("reject".equalsIgnoreCase(action)) {
            friend.setStatus(FriendStatus.REJECTED);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action must be 'accept' or 'reject'");
        }

        Friend updated = friendRepository.save(friend);
        if (updated.getStatus() == FriendStatus.ACCEPTED) {
            notificationService.createNotification(
                    updated.getRequester().getId(),
                    responder.getId(),
                    NotificationType.FRIEND_ACCEPTED,
                    "Friend request accepted",
                    profileService.displayName(responder) + " accepted your friend request.",
                    "/users/" + responder.getId());
        }
        return mapToResponse(updated, responder.getId());
    }

    @Transactional
    public void removeFriend(UUID otherUserId, String currentUserEmail) {
        Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Friend friend = friendRepository.findBetween(currentUser.getId(), otherUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend connection not found"));

        if (friend.getStatus() != FriendStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users are not friends");
        }

        friendRepository.delete(friend);
    }

    @Transactional(readOnly = true)
    public Page<ProfileResponse> getFriends(UUID userId, Pageable pageable) {
        return friendRepository.findAcceptedFriends(userId, pageable)
                .map(f -> {
                    Profile other = f.getRequester().getId().equals(userId) ? f.getReceiver() : f.getRequester();
                    return profileService.mapToResponse(other);
                });
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestResponse> getIncomingRequests(String currentUserEmail, Pageable pageable) {
        Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return friendRepository.findByReceiverIdAndStatus(currentUser.getId(), FriendStatus.PENDING, pageable)
                .map(f -> mapToResponse(f, currentUser.getId()));
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestResponse> getOutgoingRequests(String currentUserEmail, Pageable pageable) {
        Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return friendRepository.findByRequesterIdAndStatus(currentUser.getId(), FriendStatus.PENDING, pageable)
                .map(f -> mapToResponse(f, currentUser.getId()));
    }

    @Transactional(readOnly = true)
    public boolean areFriends(UUID userIdA, UUID userIdB) {
        return friendRepository.areFriends(userIdA, userIdB);
    }

    /**
     * Get the friendship status between the current user and another user.
     * Returns null if no relationship exists.
     */
    @Transactional(readOnly = true)
    public FriendRequestResponse getFriendStatus(UUID otherUserId, String currentUserEmail) {
        Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return friendRepository.findBetween(currentUser.getId(), otherUserId)
                .map(f -> mapToResponse(f, currentUser.getId()))
                .orElse(null);
    }

    private FriendRequestResponse mapToResponse(Friend friend, UUID viewerId) {
        Profile other = friend.getRequester().getId().equals(viewerId)
                ? friend.getReceiver()
                : friend.getRequester();

        return FriendRequestResponse.builder()
                .id(friend.getId())
                .status(friend.getStatus())
                .isSentByMe(friend.getRequester().getId().equals(viewerId))
                .otherUser(profileService.mapToResponse(other))
                .createdAt(friend.getCreatedAt())
                .build();
    }

}
