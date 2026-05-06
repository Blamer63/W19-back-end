package com.example.demo.controller;

import com.example.demo.entity.Language;
import com.example.demo.entity.Post;
import com.example.demo.entity.Profile;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.service.translation.TranslationClient;
import com.example.demo.service.translation.TranslationProviderException;
import com.example.demo.service.translation.TranslationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private com.example.demo.repository.ContentReportRepository contentReportRepository;
    @Autowired
    private com.example.demo.repository.PostTranslationRepository postTranslationRepository;
    @Autowired
    private com.example.demo.repository.PostReactionRepository postReactionRepository;
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
    private com.example.demo.repository.RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private com.example.demo.repository.LanguageRepository languageRepository;

    @MockitoBean
    private TranslationClient translationClient;

    private Profile testUser;

    @BeforeEach
    void setUp() {
        reset(translationClient);
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

        testUser = Profile.builder()
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hashed_password")
                .displayName("Test User")
                .build();
        profileRepository.save(testUser);

        saveLanguage("en", "English");
        saveLanguage("ko", "Korean");
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldCreatePostSuccessfully() throws Exception {
        mockMvc.perform(multipart("/api/posts")
                .param("content", "Hello world!")
                .param("original_language", "en"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Hello world!"))
                .andExpect(jsonPath("$.author.username").value("testuser"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldGetFeedSuccessfully() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldTranslatePostSuccessfully() throws Exception {
        Post post = savePost("Hello world", "en");

        when(translationClient.translate("Hello world", "en", "ko"))
                .thenReturn(TranslationResult.builder()
                        .translatedText("안녕하세요")
                        .detectedSourceLanguage("en")
                        .build());

        mockMvc.perform(get("/api/posts/{postId}/translations", post.getId())
                .param("target_language", "ko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language_code").value("ko"))
                .andExpect(jsonPath("$.translated_content").value("안녕하세요"));

        verify(translationClient).translate("Hello world", "en", "ko");
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldReturnBadRequestWhenTargetLanguageMissing() throws Exception {
        Post post = savePost("Hello world", "en");

        mockMvc.perform(get("/api/posts/{postId}/translations", post.getId()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(translationClient);
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldReturnBadRequestWhenTargetLanguageUnsupported() throws Exception {
        Post post = savePost("Hello world", "en");

        mockMvc.perform(get("/api/posts/{postId}/translations", post.getId())
                .param("target_language", "xx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported target language: xx"));

        verifyNoInteractions(translationClient);
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldReturnNotFoundWhenTranslatingMissingPost() throws Exception {
        UUID missingPostId = UUID.randomUUID();

        mockMvc.perform(get("/api/posts/{postId}/translations", missingPostId)
                .param("target_language", "ko"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Post not found"));

        verifyNoInteractions(translationClient);
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldReturnServiceUnavailableWhenTranslationProviderFails() throws Exception {
        Post post = savePost("Hello world", "en");

        when(translationClient.translate("Hello world", "en", "ko"))
                .thenThrow(new TranslationProviderException("Translation provider is unavailable"));

        mockMvc.perform(get("/api/posts/{postId}/translations", post.getId())
                .param("target_language", "ko"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Translation provider is unavailable"));
    }

    private void saveLanguage(String code, String name) {
        languageRepository.save(Language.builder()
                .code(code)
                .name(name)
                .nativeName(name)
                .flagEmoji("🌐")
                .build());
    }

    private Post savePost(String content, String originalLanguage) {
        return postRepository.save(Post.builder()
                .author(testUser)
                .content(content)
                .originalLanguage(originalLanguage)
                .build());
    }
}
