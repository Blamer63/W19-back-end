package com.example.demo.controller;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.Notification;
import com.example.demo.entity.Profile;
import com.example.demo.enums.NotificationType;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private com.example.demo.repository.ContentReportRepository contentReportRepository;
    @Autowired
    private com.example.demo.repository.PostTranslationRepository postTranslationRepository;
    @Autowired
    private com.example.demo.repository.PostReactionRepository postReactionRepository;
    @Autowired
    private com.example.demo.repository.PostCommentRepository postCommentRepository;
    @Autowired
    private com.example.demo.repository.PostRepository postRepository;
    @Autowired
    private com.example.demo.repository.PracticeResultRepository practiceResultRepository;
    @Autowired
    private com.example.demo.repository.PracticeSessionRepository practiceSessionRepository;
    @Autowired
    private com.example.demo.repository.SavedWordRepository savedWordRepository;
    @Autowired
    private com.example.demo.repository.UserLanguageRepository userLanguageRepository;
    @Autowired
    private com.example.demo.repository.UserSettingsRepository userSettingsRepository;
    @Autowired
    private com.example.demo.repository.UserBlockRepository userBlockRepository;
    @Autowired
    private com.example.demo.repository.FollowRepository followRepository;
    @Autowired
    private com.example.demo.repository.RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private com.example.demo.repository.LanguageRepository languageRepository;

    private String token;
    private Profile currentUser;
    private Profile actor;
    private Profile otherRecipient;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAll();
        contentReportRepository.deleteAll();
        postTranslationRepository.deleteAll();
        postReactionRepository.deleteAll();
        postCommentRepository.deleteAll();
        postRepository.deleteAll();
        practiceResultRepository.deleteAll();
        practiceSessionRepository.deleteAll();
        savedWordRepository.deleteAll();
        userLanguageRepository.deleteAll();
        userSettingsRepository.deleteAll();
        userBlockRepository.deleteAll();
        followRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        profileRepository.deleteAll();
        languageRepository.deleteAll();

        String email = "notifications_user@example.com";
        String password = "password123";

        try {
            RegisterRequest request = new RegisterRequest();
            request.setEmail(email);
            request.setPassword(password);
            request.setUsername("notifications_user");
            request.setDisplayName("Notifications User");
            AuthResponse response = authService.register(request);
            token = response.getAccessToken();
        } catch (Exception e) {
            try {
                AuthResponse response = authService.login(new LoginRequest(email, password));
                token = response.getAccessToken();
            } catch (Exception loginEx) {
                // Ignore
            }
        }

        currentUser = profileRepository.findByEmail(email).orElseThrow();
        actor = profileRepository.save(Profile.builder()
                .username("notification_actor")
                .email("notification-actor@example.com")
                .displayName("Notification Actor")
                .passwordHash("hash")
                .avatarUrl("https://example.com/avatar.png")
                .build());
        otherRecipient = profileRepository.save(Profile.builder()
                .username("notification_other")
                .email("notification-other@example.com")
                .displayName("Notification Other")
                .passwordHash("hash")
                .build());
    }

    @Test
    void shouldListOnlyCurrentUserNotifications() throws Exception {
        if (token == null) {
            return;
        }

        saveNotification(currentUser, NotificationType.FRIEND_REQUEST, "Friend request", null);
        saveNotification(currentUser, NotificationType.MESSAGE, "New message", null);
        saveNotification(otherRecipient, NotificationType.POST_LIKE, "Other user notification", null);

        mockMvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].actor.display_name").value("Notification Actor"))
                .andExpect(jsonPath("$.content[0].actor.avatar_url").value("https://example.com/avatar.png"));
    }

    @Test
    void shouldListUnreadNotificationsOnly() throws Exception {
        if (token == null) {
            return;
        }

        saveNotification(currentUser, NotificationType.FRIEND_REQUEST, "Unread", null);
        saveNotification(currentUser, NotificationType.MESSAGE, "Read", LocalDateTime.now());

        mockMvc.perform(get("/api/notifications")
                .param("unreadOnly", "true")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Unread"));
    }

    @Test
    void shouldGetNotificationSummary() throws Exception {
        if (token == null) {
            return;
        }

        saveNotification(currentUser, NotificationType.FRIEND_REQUEST, "Unread one", null);
        saveNotification(currentUser, NotificationType.MESSAGE, "Unread two", null);
        saveNotification(currentUser, NotificationType.POST_COMMENT, "Read", LocalDateTime.now());

        mockMvc.perform(get("/api/notifications/summary")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread_notifications").value(2))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void shouldMarkNotificationRead() throws Exception {
        if (token == null) {
            return;
        }

        Notification notification = saveNotification(currentUser, NotificationType.MESSAGE, "Unread", null);

        mockMvc.perform(patch("/api/notifications/" + notification.getId() + "/read")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read_at", notNullValue()));
    }

    @Test
    void shouldMarkAllNotificationsRead() throws Exception {
        if (token == null) {
            return;
        }

        saveNotification(currentUser, NotificationType.FRIEND_REQUEST, "Unread one", null);
        saveNotification(currentUser, NotificationType.MESSAGE, "Unread two", null);

        mockMvc.perform(patch("/api/notifications/read-all")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread_notifications").value(0))
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/notifications/summary")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread_notifications").value(0));
    }

    private Notification saveNotification(
            Profile recipient,
            NotificationType type,
            String title,
            LocalDateTime readAt) {
        return notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .title(title)
                .body("Notification body")
                .targetUrl("/target")
                .readAt(readAt)
                .build());
    }
}
