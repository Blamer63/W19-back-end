package com.example.demo.controller;

import com.example.demo.dto.NotificationCenterSummaryResponse;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> listNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(notificationService.listNotifications(authentication.getName(), unreadOnly, pageable));
    }

    @GetMapping("/summary")
    public ResponseEntity<NotificationCenterSummaryResponse> getSummary(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getSummary(authentication.getName()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(notificationService.markRead(id, authentication.getName()));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<NotificationCenterSummaryResponse> markAllRead(Authentication authentication) {
        return ResponseEntity.ok(notificationService.markAllRead(authentication.getName()));
    }
}
