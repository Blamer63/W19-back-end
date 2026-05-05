package com.example.demo.controller;

import com.example.demo.dto.StartSessionRequest;
import com.example.demo.dto.SubmitResultRequest;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.SavedWord;
import com.example.demo.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class PracticeControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private ProfileRepository profileRepository;

        @Autowired
        private SavedWordRepository savedWordRepository;

        @Autowired
        private PracticeSessionRepository practiceSessionRepository;

        @Autowired
        private PracticeResultRepository practiceResultRepository;

        @Autowired
        private LanguageRepository languageRepository;

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
        private com.example.demo.repository.UserLanguageRepository userLanguageRepository;
        @Autowired
        private com.example.demo.repository.UserSettingsRepository userSettingsRepository;
        @Autowired
        private com.example.demo.repository.UserBlockRepository userBlockRepository;
        @Autowired
        private com.example.demo.repository.FollowRepository followRepository;
        @Autowired
        private com.example.demo.repository.RefreshTokenRepository refreshTokenRepository;

        private Profile testUser;
        private Language japanese;

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

                // Create test language
                japanese = Language.builder()
                                .code("ja")
                                .name("Japanese")
                                .flagEmoji("🇯🇵")
                                .build();
                languageRepository.save(japanese);

                // Create test words for practice
                for (int i = 0; i < 15; i++) {
                        SavedWord word = SavedWord.builder()
                                        .user(testUser)
                                        .word("word" + i)
                                        .translation("translation" + i)
                                        .languageCode("ja")
                                        .masteryLevel(i * 5)
                                        .build();
                        savedWordRepository.save(word);
                }
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void startSession_ValidSize_Success() throws Exception {
                StartSessionRequest request = new StartSessionRequest();
                request.setSessionSize(10);
                request.setLanguageCode("ja");

                mockMvc.perform(post("/api/learn/sessions/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.session_id").exists())
                                .andExpect(jsonPath("$.words").isArray())
                                .andExpect(jsonPath("$.words.length()").value(10));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void startSession_InvalidSize_Returns500() throws Exception {
                StartSessionRequest request = new StartSessionRequest();
                request.setSessionSize(7); // Invalid size

                mockMvc.perform(post("/api/learn/sessions/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void startSession_NotEnoughWords_Returns500() throws Exception {
                // Delete all words
                savedWordRepository.deleteAll();

                StartSessionRequest request = new StartSessionRequest();
                request.setSessionSize(10);

                mockMvc.perform(post("/api/learn/sessions/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getPracticeHistory_Success() throws Exception {
                mockMvc.perform(get("/api/learn/sessions")
                                .param("page", "0")
                                .param("size", "20"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray());
        }
}
