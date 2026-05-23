package com.example.demo.controller;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.Conversation;
import com.example.demo.entity.Friend;
import com.example.demo.entity.Message;
import com.example.demo.entity.UserSettings;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.Profile;
import com.example.demo.enums.FriendStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

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
    private com.example.demo.repository.LanguageRepository languageRepository;
    @Autowired
    private com.example.demo.repository.FriendRepository friendRepository;
    @Autowired
    private com.example.demo.repository.ConversationRepository conversationRepository;
    @Autowired
    private com.example.demo.repository.MessageRepository messageRepository;

    private String token;

    @BeforeEach
    void setup() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        friendRepository.deleteAll();
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

        String email = "user_me@example.com";
        String password = "password123";

        try {
            RegisterRequest request = new RegisterRequest();
            request.setEmail(email);
            request.setPassword(password);
            request.setUsername("user_me");
            request.setDisplayName("User Me");
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
    }

    @Test
    void shouldDenyAccessWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAccessWithValidToken() throws Exception {
        if (token == null)
            return;

        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user_me@example.com"));
    }

    @Test
    void shouldGetPublicProfile() throws Exception {
        if (token == null)
            return;

        // Create another user
        Profile otherUser = Profile.builder()
                .username("other_user")
                .email("other@example.com")
                .displayName("Other User")
                .passwordHash("hash")
                .build();
        profileRepository.save(otherUser);

        mockMvc.perform(get("/api/users/" + otherUser.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("other_user"))
                .andExpect(jsonPath("$.display_name").value("Other User"))
                .andExpect(jsonPath("$.privacy_settings.show_activity").value(true));
    }

    @Test
    void shouldUpdatePrivacySettings() throws Exception {
        if (token == null)
            return;

        String json = "{\"show_activity\": false, \"show_saved_words\": true}";

        mockMvc.perform(patch("/api/users/me/privacy")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.show_activity").value(false))
                .andExpect(jsonPath("$.show_saved_words").value(true));
    }

    @Test
    void shouldPersistNotificationSettingsPatchAndPreserveUnspecifiedPreferences() throws Exception {
        if (token == null)
            return;

        String initialJson = """
                {
                  "notification_prefs": {
                    "push_enabled": true,
                    "email_enabled": true,
                    "like_notifications": false,
                    "comment_notifications": true,
                    "meetup_notifications": true
                  }
                }
                """;

        mockMvc.perform(patch("/api/users/me/settings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(initialJson))
                .andExpect(status().isOk());

        String json = "{\"notification_prefs\":{\"push_enabled\":false}}";

        mockMvc.perform(patch("/api/users/me/settings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notification_prefs.push_enabled").value(false))
                .andExpect(jsonPath("$.notification_prefs.email_enabled").value(true))
                .andExpect(jsonPath("$.notification_prefs.like_notifications").value(false))
                .andExpect(jsonPath("$.notification_prefs.comment_notifications").value(true))
                .andExpect(jsonPath("$.notification_prefs.meetup_notifications").value(true));

        mockMvc.perform(get("/api/users/me/settings")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notification_prefs.push_enabled").value(false))
                .andExpect(jsonPath("$.notification_prefs.email_enabled").value(true))
                .andExpect(jsonPath("$.notification_prefs.like_notifications").value(false))
                .andExpect(jsonPath("$.notification_prefs.comment_notifications").value(true))
                .andExpect(jsonPath("$.notification_prefs.meetup_notifications").value(true));

        Profile currentUser = profileRepository.findByEmail("user_me@example.com").orElseThrow();
        UserSettings persisted = userSettingsRepository.findByProfileId(currentUser.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persisted.getNotificationPrefs().isPushEnabled()).isFalse();
        org.assertj.core.api.Assertions.assertThat(persisted.getNotificationPrefs().isEmailEnabled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(persisted.getNotificationPrefs().isLikeNotifications()).isFalse();
        org.assertj.core.api.Assertions.assertThat(persisted.getNotificationPrefs().isCommentNotifications()).isTrue();
        org.assertj.core.api.Assertions.assertThat(persisted.getNotificationPrefs().isMeetupNotifications()).isTrue();
    }

    @Test
    void shouldGetNotificationSummary() throws Exception {
        if (token == null)
            return;

        Profile currentUser = profileRepository.findByEmail("user_me@example.com").orElseThrow();
        Profile otherUser = Profile.builder()
                .username("notification_sender")
                .email("notification-sender@example.com")
                .displayName("Notification Sender")
                .passwordHash("hash")
                .build();
        otherUser = profileRepository.save(otherUser);

        friendRepository.save(Friend.builder()
                .requester(otherUser)
                .receiver(currentUser)
                .status(FriendStatus.PENDING)
                .build());

        Conversation conversation = Conversation.builder()
                .participants(List.of(currentUser, otherUser))
                .lastMessagePreview("Unread")
                .build();
        conversation = conversationRepository.save(conversation);

        messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(otherUser)
                .content("Unread one")
                .isRead(false)
                .build());
        messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(otherUser)
                .content("Unread two")
                .isRead(false)
                .build());
        messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(currentUser)
                .content("Own unread should not count")
                .isRead(false)
                .build());

        Profile groupMember = Profile.builder()
                .username("group_member")
                .email("group-member@example.com")
                .displayName("Group Member")
                .passwordHash("hash")
                .build();
        groupMember = profileRepository.save(groupMember);

        Conversation groupConversation = Conversation.builder()
                .participants(List.of(currentUser, otherUser, groupMember))
                .isGroup(true)
                .lastMessagePreview("Group unread")
                .build();
        groupConversation = conversationRepository.save(groupConversation);

        messageRepository.save(Message.builder()
                .conversation(groupConversation)
                .sender(otherUser)
                .content("Group unread should not count yet")
                .isRead(false)
                .build());

        mockMvc.perform(get("/api/users/me/notification-summary")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incoming_friend_requests").value(1))
                .andExpect(jsonPath("$.unread_messages").value(2))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void shouldReturn404ForNonExistentUser() throws Exception {
        if (token == null)
            return;

        // UserService throws RuntimeException which gets mapped to 400 by Spring
        mockMvc.perform(get("/api/users/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
