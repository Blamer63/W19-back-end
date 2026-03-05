package com.example.demo.controller;

import com.example.demo.dto.FriendRequestResponse;
import com.example.demo.dto.ProfileResponse;
import com.example.demo.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    // POST /api/users/{id}/friend-request — send a friend request
    @PostMapping("/{id}/friend-request")
    public ResponseEntity<FriendRequestResponse> sendFriendRequest(
            @PathVariable UUID id,
            Authentication authentication) {
        FriendRequestResponse response = friendService.sendRequest(id, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // PATCH /api/users/me/friend-requests/{friendId}?action=accept|reject
    @PatchMapping("/me/friend-requests/{friendId}")
    public ResponseEntity<FriendRequestResponse> respondToRequest(
            @PathVariable UUID friendId,
            @RequestParam String action,
            Authentication authentication) {
        FriendRequestResponse response = friendService.respondToRequest(friendId, action, authentication.getName());
        return ResponseEntity.ok(response);
    }

    // DELETE /api/users/{id}/friend — remove a friend
    @DeleteMapping("/{id}/friend")
    public ResponseEntity<Void> removeFriend(
            @PathVariable UUID id,
            Authentication authentication) {
        friendService.removeFriend(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    // GET /api/users/{id}/friends — list friends of a user
    @GetMapping("/{id}/friends")
    public ResponseEntity<Page<ProfileResponse>> getFriends(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(friendService.getFriends(id, pageable));
    }

    // GET /api/users/me/friend-requests/incoming — pending requests sent to me
    @GetMapping("/me/friend-requests/incoming")
    public ResponseEntity<Page<FriendRequestResponse>> getIncomingRequests(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(friendService.getIncomingRequests(authentication.getName(), pageable));
    }

    // GET /api/users/me/friend-requests/outgoing — requests I have sent
    @GetMapping("/me/friend-requests/outgoing")
    public ResponseEntity<Page<FriendRequestResponse>> getOutgoingRequests(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(friendService.getOutgoingRequests(authentication.getName(), pageable));
    }

    // GET /api/users/{id}/friend-status — get friendship status with a specific
    // user
    @GetMapping("/{id}/friend-status")
    public ResponseEntity<FriendRequestResponse> getFriendStatus(
            @PathVariable UUID id,
            Authentication authentication) {
        FriendRequestResponse status = friendService.getFriendStatus(id, authentication.getName());
        if (status == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(status);
    }
}
