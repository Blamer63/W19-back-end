package com.example.demo.controller;

import com.example.demo.dto.CreateCommentRequest;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostComment;
import com.example.demo.entity.Profile;
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
public class CommentControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private ProfileRepository profileRepository;

        @Autowired
        private PostRepository postRepository;

        @Autowired
        private PostCommentRepository postCommentRepository;

        @Autowired
        private com.example.demo.repository.ContentReportRepository contentReportRepository;
        @Autowired
        private com.example.demo.repository.PostTranslationRepository postTranslationRepository;
        @Autowired
        private com.example.demo.repository.PostReactionRepository postReactionRepository;
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

        private Profile testUser;
        private Profile otherUser;
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

                // Create test users
                testUser = Profile.builder()
                                .username("testuser")
                                .email("test@example.com")
                                .passwordHash("hashed_password")
                                .displayName("Test User")
                                .build();
                profileRepository.save(testUser);

                otherUser = Profile.builder()
                                .username("otheruser")
                                .email("other@example.com")
                                .passwordHash("hashed_password")
                                .displayName("Other User")
                                .build();
                profileRepository.save(otherUser);

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
        void getComments_Success() throws Exception {
                // Create test comments
                PostComment comment1 = PostComment.builder()
                                .post(testPost)
                                .author(testUser)
                                .content("First comment")
                                .build();
                postCommentRepository.save(comment1);

                PostComment comment2 = PostComment.builder()
                                .post(testPost)
                                .author(otherUser)
                                .content("Second comment")
                                .build();
                postCommentRepository.save(comment2);

                mockMvc.perform(get("/api/posts/" + testPost.getId() + "/comments")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void addComment_Success() throws Exception {
                CreateCommentRequest request = new CreateCommentRequest();
                request.setContent("This is a test comment");

                mockMvc.perform(post("/api/posts/" + testPost.getId() + "/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.content").value("This is a test comment"))
                                .andExpect(jsonPath("$.author.display_name").value("Test User"));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void deleteComment_OwnComment_Success() throws Exception {
                PostComment comment = PostComment.builder()
                                .post(testPost)
                                .author(testUser)
                                .content("My comment")
                                .build();
                PostComment saved = postCommentRepository.save(comment);

                mockMvc.perform(delete("/api/posts/" + testPost.getId() + "/comments/" + saved.getId()))
                                .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void deleteComment_OthersComment_Returns500() throws Exception {
                PostComment comment = PostComment.builder()
                                .post(testPost)
                                .author(otherUser)
                                .content("Other user's comment")
                                .build();
                PostComment saved = postCommentRepository.save(comment);

                mockMvc.perform(delete("/api/posts/" + testPost.getId() + "/comments/" + saved.getId()))
                                .andExpect(status().isBadRequest());
        }
}
