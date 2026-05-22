package com.example.demo.service;

import com.example.demo.dto.AdminFeatureStatusResponse;
import com.example.demo.dto.AdminMetricResponse;
import com.example.demo.dto.AdminSummaryResponse;
import com.example.demo.repository.ContentReportRepository;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.MeetupRepository;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.SavedWordRepository;
import com.example.demo.repository.ScanSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final ProfileRepository profileRepository;
    private final PostRepository postRepository;
    private final MeetupRepository meetupRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SavedWordRepository savedWordRepository;
    private final ScanSessionRepository scanSessionRepository;
    private final NotificationRepository notificationRepository;
    private final ContentReportRepository contentReportRepository;

    @Transactional(readOnly = true)
    public AdminSummaryResponse getSummary() {
        return AdminSummaryResponse.builder()
                .generatedAt(LocalDateTime.now())
                .metrics(List.of(
                        metric("users", "Users", profileRepository.count(), "Registered profiles"),
                        metric("posts", "Posts", postRepository.count(), "Published and moderated posts"),
                        metric("meetups", "Meetups", meetupRepository.count(), "Created language meetups"),
                        metric("conversations", "Conversations", conversationRepository.count(), "Direct and group chats"),
                        metric("messages", "Messages", messageRepository.count(), "Stored chat messages"),
                        metric("saved_words", "Saved Words", savedWordRepository.count(), "Learner vocabulary entries"),
                        metric("scan_sessions", "Scan Sessions", scanSessionRepository.count(), "Completed scanner sessions"),
                        metric("notifications", "Notifications", notificationRepository.count(), "Persisted notification records"),
                        metric("reports", "Reports", contentReportRepository.count(), "Submitted content reports")))
                .futureFeatures(List.of(
                        plannedFeature("user-management", "User Management", "Search, review, suspend, or update users."),
                        plannedFeature("role-management", "Role Management", "Assign moderator and admin responsibilities."),
                        plannedFeature("content-moderation", "Content Moderation", "Review posts, comments, and user reports."),
                        plannedFeature("meetup-moderation", "Meetup Moderation", "Review, hide, or cancel unsafe meetups."),
                        plannedFeature("scanner-analytics", "Scanner Analytics", "Track scan volume, confidence, and fallback behavior."),
                        plannedFeature("notification-operations", "Notification Operations", "Review notification health and future announcements."),
                        plannedFeature("audit-logs", "Audit Logs", "Track sensitive admin actions."),
                        plannedFeature("app-configuration", "App Configuration", "Manage operational settings without redeploying.")))
                .build();
    }

    private AdminMetricResponse metric(String key, String label, long value, String description) {
        return AdminMetricResponse.builder()
                .key(key)
                .label(label)
                .value(value)
                .description(description)
                .build();
    }

    private AdminFeatureStatusResponse plannedFeature(String key, String label, String description) {
        return AdminFeatureStatusResponse.builder()
                .key(key)
                .label(label)
                .status("PLANNED")
                .description(description)
                .enabled(false)
                .build();
    }
}
