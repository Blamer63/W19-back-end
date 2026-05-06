package com.example.demo.controller;

import com.example.demo.dto.PostReactionRequest;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostReaction;
import com.example.demo.entity.Profile;
import com.example.demo.enums.ReactionType;
import com.example.demo.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
public class ReactionControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private ProfileRepository profileRepository;

        @Autowired
        private PostRepository postRepository;

        @Autowired
        private com.example.demo.repository.ContentReportRepository contentReportRepository;
        @Autowired
        private com.example.demo.repository.PostTranslationRepository postTranslationRepository;
        @Autowired
        private com.example.demo.repository.PostCommentRepository postCommentRepository;
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
        private com.example.demo.repository.PostReactionRepository postReactionRepository;
        @Autowired
        private com.example.demo.repository.RefreshTokenRepository refreshTokenRepository;
        @Autowired
        private com.example.demo.repository.LanguageRepository languageRepository;

        private Profile testUser;
        private Post testPost;

        @BeforeEach
        void setUp() {

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

                // Create test user
                testUser = Profile.builder()
                                .username("testuser")
                                .email("test@example.com")
                                .passwordHash("hashed_password")
                                .displayName("Test User")
                                .build();
                profileRepository.save(testUser);

                // Create test post
                testPost = Post.builder()
                                .author(testUser)
                                .content("Test post content")
                                .originalLanguage("en")
                                .build();
                postRepository.save(testPost);
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void reactToPost_Success() throws Exception {
                PostReactionRequest request = new PostReactionRequest();
                request.setReaction(ReactionType.LIKE);

                mockMvc.perform(post("/api/posts/" + testPost.getId() + "/reactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.user_reaction").value("LIKE"))
                                .andExpect(jsonPath("$.likes").value(1))
                                .andExpect(jsonPath("$.comments").value(0));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void reactToPost_ChangeReaction_Success() throws Exception {
                // First add a LIKE
                PostReaction.PostReactionId reactionId = new PostReaction.PostReactionId(testPost.getId(),
                                testUser.getId());
                PostReaction existingReaction = PostReaction.builder()
                                .id(reactionId)
                                .post(testPost)
                                .profile(testUser)
                                .type(ReactionType.LIKE)
                                .build();
                postReactionRepository.save(existingReaction);

                // Change to LOVE
                PostReactionRequest request = new PostReactionRequest();
                request.setReaction(ReactionType.LOVE);

                mockMvc.perform(post("/api/posts/" + testPost.getId() + "/reactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.user_reaction").value("LOVE"))
                                .andExpect(jsonPath("$.likes").value(1));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void removeReaction_Success() throws Exception {
                // First add a reaction
                PostReaction.PostReactionId reactionId = new PostReaction.PostReactionId(testPost.getId(),
                                testUser.getId());
                PostReaction reaction = PostReaction.builder()
                                .id(reactionId)
                                .post(testPost)
                                .profile(testUser)
                                .type(ReactionType.LIKE)
                                .build();
                postReactionRepository.save(reaction);

                mockMvc.perform(delete("/api/posts/" + testPost.getId() + "/reactions"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.user_reaction").doesNotExist())
                                .andExpect(jsonPath("$.likes").value(0));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void removeReaction_NoExistingReaction_Success() throws Exception {
                // Try to remove reaction when none exists
                mockMvc.perform(delete("/api/posts/" + testPost.getId() + "/reactions"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.user_reaction").doesNotExist())
                                .andExpect(jsonPath("$.likes").value(0));
        }
}
