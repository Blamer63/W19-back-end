package com.example.demo.controller;

import com.example.demo.entity.Profile;
import com.example.demo.entity.UserFollow;
import com.example.demo.repository.FollowRepository;
import com.example.demo.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FollowControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ProfileRepository profileRepository;

        @Autowired
        private FollowRepository followRepository;

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
        private com.example.demo.repository.RefreshTokenRepository refreshTokenRepository;
        @Autowired
        private com.example.demo.repository.LanguageRepository languageRepository;

        private Profile userA;
        private Profile userB;

        @BeforeEach
        void setUp() {
                // Clean up all data to avoid FK constraint issues
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

                userA = Profile.builder()
                                .username("userA")
                                .email("usera@example.com")
                                .passwordHash("pass")
                                .displayName("User A")
                                .build();
                profileRepository.save(userA);

                userB = Profile.builder()
                                .username("userB")
                                .email("userb@example.com")
                                .passwordHash("pass")
                                .displayName("User B")
                                .build();
                profileRepository.save(userB);
        }

        @Test
        @WithMockUser(username = "usera@example.com")
        void followUser_Success() throws Exception {
                mockMvc.perform(post("/api/users/" + userB.getId() + "/follow"))
                                .andExpect(status().isOk());

                assert (followRepository.existsByFollowerIdAndFollowingId(userA.getId(), userB.getId()));
        }

        @Test
        @WithMockUser(username = "usera@example.com")
        void unfollowUser_Success() throws Exception {
                // Setup initial follow
                UserFollow follow = UserFollow.builder()
                                .follower(userA)
                                .following(userB)
                                .build();
                followRepository.save(follow);

                mockMvc.perform(delete("/api/users/" + userB.getId() + "/follow"))
                                .andExpect(status().isOk());

                assert (!followRepository.existsByFollowerIdAndFollowingId(userA.getId(), userB.getId()));
        }

        @Test
        @WithMockUser(username = "usera@example.com")
        void getFollowing_Success() throws Exception {
                UserFollow follow = UserFollow.builder()
                                .follower(userA)
                                .following(userB)
                                .build();
                followRepository.save(follow);

                mockMvc.perform(get("/api/users/" + userA.getId() + "/following"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].username").value("userB"));
        }

        @Test
        @WithMockUser(username = "userb@example.com")
        void getFollowers_Success() throws Exception {
                UserFollow follow = UserFollow.builder()
                                .follower(userA)
                                .following(userB)
                                .build();
                followRepository.save(follow);

                mockMvc.perform(get("/api/users/" + userB.getId() + "/followers"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].username").value("userA"));
        }
}
