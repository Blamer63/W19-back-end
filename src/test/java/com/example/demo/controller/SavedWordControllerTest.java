package com.example.demo.controller;

import com.example.demo.dto.CreateWordRequest;
import com.example.demo.dto.UpdateWordRequest;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.SavedWord;
import com.example.demo.enums.SourceType;
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
public class SavedWordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private SavedWordRepository savedWordRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private ScannerTranslationCacheRepository scannerTranslationCacheRepository;

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
    private com.example.demo.repository.NotificationRepository notificationRepository;

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
        notificationRepository.deleteAll();
        scannerTranslationCacheRepository.deleteAll();
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
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void saveWord_Success() throws Exception {
        CreateWordRequest request = new CreateWordRequest();
        request.setWord("さくら");
        request.setTranslation("cherry blossom");
        request.setLanguageCode("ja");
        request.setSource(SourceType.MANUAL);
        request.setTopic("nature");

        mockMvc.perform(post("/api/words")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.word").value("さくら"))
                .andExpect(jsonPath("$.translation").value("cherry blossom"))
                .andExpect(jsonPath("$.language_code").value("ja"))
                .andExpect(jsonPath("$.topic").value("nature"))
                .andExpect(jsonPath("$.mastery_level").value(0));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void saveWord_FromScanner_Success() throws Exception {
        Language korean = Language.builder()
                .code("ko")
                .name("Korean")
                .flagEmoji("🇰🇷")
                .build();
        languageRepository.save(korean);

        String scannerPayload = """
                {
                  "word": "apple",
                  "translation": "사과",
                  "language_code": "ko",
                  "source": "SCANNER",
                  "context": "Detected in photo with 94% confidence"
                }
                """;

        mockMvc.perform(post("/api/words")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scannerPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.word").value("apple"))
                .andExpect(jsonPath("$.translation").value("사과"))
                .andExpect(jsonPath("$.language_code").value("ko"))
                .andExpect(jsonPath("$.language_name").value("Korean"))
                .andExpect(jsonPath("$.source").value("SCANNER"))
                .andExpect(jsonPath("$.context").value("Detected in photo with 94% confidence"))
                .andExpect(jsonPath("$.mastery_level").value(0));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void saveWord_MissingSource_ReturnsBadRequest() throws Exception {
        // Save word first
        SavedWord existing = SavedWord.builder()
                .user(testUser)
                .word("さくら")
                .translation("cherry blossom")
                .languageCode("ja")
                .build();
        savedWordRepository.save(existing);

        // Try to save duplicate
        CreateWordRequest request = new CreateWordRequest();
        request.setWord("さくら");
        request.setTranslation("cherry blossom");
        request.setLanguageCode("ja");

        mockMvc.perform(post("/api/words")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void saveWord_DuplicateScannerWord_ReturnsConflict() throws Exception {
        Language korean = Language.builder()
                .code("ko")
                .name("Korean")
                .flagEmoji("KR")
                .build();
        languageRepository.save(korean);

        SavedWord existing = SavedWord.builder()
                .user(testUser)
                .word("chair")
                .translation("uija")
                .languageCode("ko")
                .source(SourceType.SCANNER)
                .context("Detected in photo with 91% confidence")
                .build();
        savedWordRepository.save(existing);

        String scannerPayload = """
                {
                  "word": "chair",
                  "translation": "uija",
                  "language_code": "ko",
                  "source": "SCANNER",
                  "context": "Detected in photo with 76% confidence"
                }
                """;

        mockMvc.perform(post("/api/words")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scannerPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Word already saved"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getWords_WithPagination_Success() throws Exception {
        // Create test words
        SavedWord word1 = SavedWord.builder()
                .user(testUser)
                .word("さくら")
                .translation("cherry blossom")
                .languageCode("ja")
                .masteryLevel(50)
                .build();
        savedWordRepository.save(word1);

        SavedWord word2 = SavedWord.builder()
                .user(testUser)
                .word("こんにちは")
                .translation("hello")
                .languageCode("ja")
                .masteryLevel(75)
                .build();
        savedWordRepository.save(word2);

        mockMvc.perform(get("/api/words")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getWords_FilterByLanguage_Success() throws Exception {
        // Create Japanese word
        SavedWord japaneseWord = SavedWord.builder()
                .user(testUser)
                .word("さくら")
                .languageCode("ja")
                .build();
        savedWordRepository.save(japaneseWord);

        // Create Spanish language and word
        Language spanish = Language.builder()
                .code("es")
                .name("Spanish")
                .flagEmoji("🇪🇸")
                .build();
        languageRepository.save(spanish);

        SavedWord spanishWord = SavedWord.builder()
                .user(testUser)
                .word("hola")
                .languageCode("es")
                .build();
        savedWordRepository.save(spanishWord);

        mockMvc.perform(get("/api/words")
                .param("language", "ja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].language_code").value("ja"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getWord_Success() throws Exception {
        SavedWord word = SavedWord.builder()
                .user(testUser)
                .word("さくら")
                .translation("cherry blossom")
                .languageCode("ja")
                .build();
        SavedWord saved = savedWordRepository.save(word);

        mockMvc.perform(get("/api/words/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.word").value("さくら"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void updateWord_Success() throws Exception {
        SavedWord word = SavedWord.builder()
                .user(testUser)
                .word("さくら")
                .translation("cherry blossom")
                .languageCode("ja")
                .build();
        SavedWord saved = savedWordRepository.save(word);

        UpdateWordRequest request = new UpdateWordRequest();
        request.setTranslation("sakura flower");
        request.setTopic("greetings");

        mockMvc.perform(patch("/api/words/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translation").value("sakura flower"))
                .andExpect(jsonPath("$.topic").value("greetings"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void deleteWord_Success() throws Exception {
        SavedWord word = SavedWord.builder()
                .user(testUser)
                .word("さくら")
                .languageCode("ja")
                .build();
        SavedWord saved = savedWordRepository.save(word);

        mockMvc.perform(delete("/api/words/" + saved.getId()))
                .andExpect(status().isNoContent());
    }
}
