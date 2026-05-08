package com.example.demo.controller;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StatsControllerTest {

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

    private Profile testUser;
    private Language japanese;

    @BeforeEach
    void setUp() {
        savedWordRepository.deleteAll();
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
    void getStats_WithWords_Success() throws Exception {
        // Create words with different mastery levels
        SavedWord word1 = SavedWord.builder()
                .user(testUser)
                .word("word1")
                .languageCode("ja")
                .masteryLevel(20) // Beginner
                .build();
        savedWordRepository.save(word1);

        SavedWord word2 = SavedWord.builder()
                .user(testUser)
                .word("word2")
                .languageCode("ja")
                .masteryLevel(40) // Learning
                .build();
        savedWordRepository.save(word2);

        SavedWord word3 = SavedWord.builder()
                .user(testUser)
                .word("word3")
                .languageCode("ja")
                .masteryLevel(60) // Familiar
                .build();
        savedWordRepository.save(word3);

        SavedWord word4 = SavedWord.builder()
                .user(testUser)
                .word("word4")
                .languageCode("ja")
                .masteryLevel(80) // Mastered
                .build();
        savedWordRepository.save(word4);

        mockMvc.perform(get("/api/learn/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_words").value(4))
                .andExpect(jsonPath("$.average_mastery").value(50))
                .andExpect(jsonPath("$.languages").isArray())
                .andExpect(jsonPath("$.languages[0].code").value("ja"))
                .andExpect(jsonPath("$.mastery_distribution.beginner").value(1))
                .andExpect(jsonPath("$.mastery_distribution.learning").value(1))
                .andExpect(jsonPath("$.mastery_distribution.familiar").value(1))
                .andExpect(jsonPath("$.mastery_distribution.mastered").value(1));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getStats_NoWords_ReturnsZeros() throws Exception {
        mockMvc.perform(get("/api/learn/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_words").value(0))
                .andExpect(jsonPath("$.average_mastery").value(0))
                .andExpect(jsonPath("$.languages").isEmpty());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getStats_IncludesScannerSavedWords() throws Exception {
        SavedWord scannerWord = SavedWord.builder()
                .user(testUser)
                .word("chair")
                .translation("isu")
                .languageCode("ja")
                .source(SourceType.SCANNER)
                .masteryLevel(30)
                .build();
        savedWordRepository.save(scannerWord);

        mockMvc.perform(get("/api/learn/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_words").value(1))
                .andExpect(jsonPath("$.average_mastery").value(30))
                .andExpect(jsonPath("$.languages[0].code").value("ja"))
                .andExpect(jsonPath("$.languages[0].word_count").value(1))
                .andExpect(jsonPath("$.mastery_distribution.learning").value(1));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getStats_VerifyMasteryDistribution() throws Exception {
        // Create 25 words with specific mastery levels
        for (int i = 0; i < 5; i++) {
            SavedWord word = SavedWord.builder()
                    .user(testUser)
                    .word("beginner" + i)
                    .languageCode("ja")
                    .masteryLevel(i * 5) // 0-20 (beginner)
                    .build();
            savedWordRepository.save(word);
        }

        for (int i = 0; i < 10; i++) {
            SavedWord word = SavedWord.builder()
                    .user(testUser)
                    .word("learning" + i)
                    .languageCode("ja")
                    .masteryLevel(26 + i * 2) // 26-44 (learning)
                    .build();
            savedWordRepository.save(word);
        }

        for (int i = 0; i < 8; i++) {
            SavedWord word = SavedWord.builder()
                    .user(testUser)
                    .word("familiar" + i)
                    .languageCode("ja")
                    .masteryLevel(51 + i * 3) // 51-72 (familiar)
                    .build();
            savedWordRepository.save(word);
        }

        for (int i = 0; i < 2; i++) {
            SavedWord word = SavedWord.builder()
                    .user(testUser)
                    .word("mastered" + i)
                    .languageCode("ja")
                    .masteryLevel(76 + i * 10) // 76-86 (mastered)
                    .build();
            savedWordRepository.save(word);
        }

        mockMvc.perform(get("/api/learn/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mastery_distribution.beginner").value(5))
                .andExpect(jsonPath("$.mastery_distribution.learning").value(10))
                .andExpect(jsonPath("$.mastery_distribution.familiar").value(8))
                .andExpect(jsonPath("$.mastery_distribution.mastered").value(2));
    }
}
