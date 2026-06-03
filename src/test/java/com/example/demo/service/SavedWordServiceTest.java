package com.example.demo.service;

import com.example.demo.dto.CreateWordRequest;
import com.example.demo.dto.SavedWordResponse;
import com.example.demo.dto.UpdateWordRequest;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.SavedWord;
import com.example.demo.enums.SourceType;
import com.example.demo.exception.DuplicateSavedWordException;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.SavedWordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedWordServiceTest {

    @Mock
    private SavedWordRepository savedWordRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private LanguageRepository languageRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SavedWordService savedWordService;

    private Profile testUser;
    private Language testLanguage;
    private SavedWord testWord;

    @BeforeEach
    void setUp() {
        testUser = new Profile();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");

        testLanguage = new Language();
        testLanguage.setCode("ja");
        testLanguage.setName("Japanese");
        testLanguage.setFlagEmoji("🇯🇵");

        testWord = SavedWord.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .word("さくら")
                .translation("cherry blossom")
                .languageCode("ja")
                .source(SourceType.POST)
                .topic("nature")
                .masteryLevel(50)
                .createdAt(Instant.now())
                .nextReview(Instant.now())
                .build();
    }

    @Test
    void saveWord_Success() {
        CreateWordRequest request = CreateWordRequest.builder()
                .word("さくら")
                .translation("cherry blossom")
                .languageCode("ja")
                .source(SourceType.POST)
                .topic("nature")
                .build();

        when(profileRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(savedWordRepository.existsByUserIdAndWordAndLanguageCode(any(), any(), any()))
                .thenReturn(false);
        when(savedWordRepository.save(any(SavedWord.class)))
                .thenReturn(testWord);
        when(languageRepository.findById("ja"))
                .thenReturn(Optional.of(testLanguage));

        SavedWordResponse response = savedWordService.saveWord("test@example.com", request);

        assertNotNull(response);
        assertEquals("さくら", response.getWord());
        assertEquals("cherry blossom", response.getTranslation());
        assertEquals("ja", response.getLanguageCode());
        assertEquals("Japanese", response.getLanguageName());
        assertEquals("🇯🇵", response.getLanguageFlag());
        assertEquals("nature", response.getTopic());
        verify(savedWordRepository).save(any(SavedWord.class));
    }

    @Test
    void saveWord_FromScanner_PersistsScannerSourceAndContext() {
        CreateWordRequest request = CreateWordRequest.builder()
                .word("apple")
                .translation("사과")
                .languageCode("ko")
                .source(SourceType.SCANNER)
                .sourceContext("Detected in photo with 94% confidence")
                .topic("food")
                .build();

        Language korean = Language.builder()
                .code("ko")
                .name("Korean")
                .flagEmoji("🇰🇷")
                .build();
        SavedWord scannerWord = SavedWord.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .word("apple")
                .translation("사과")
                .languageCode("ko")
                .source(SourceType.SCANNER)
                .context("Detected in photo with 94% confidence")
                .topic("food")
                .masteryLevel(0)
                .createdAt(Instant.now())
                .nextReview(Instant.now())
                .build();

        when(profileRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(savedWordRepository.existsByUserIdAndWordAndLanguageCode(testUser.getId(), "apple", "ko"))
                .thenReturn(false);
        when(savedWordRepository.save(any(SavedWord.class)))
                .thenReturn(scannerWord);
        when(languageRepository.findById("ko"))
                .thenReturn(Optional.of(korean));

        SavedWordResponse response = savedWordService.saveWord("test@example.com", request);

        ArgumentCaptor<SavedWord> savedWordCaptor = ArgumentCaptor.forClass(SavedWord.class);
        verify(savedWordRepository).save(savedWordCaptor.capture());
        SavedWord savedWord = savedWordCaptor.getValue();

        assertEquals("apple", savedWord.getWord());
        assertEquals("사과", savedWord.getTranslation());
        assertEquals("ko", savedWord.getLanguageCode());
        assertEquals(SourceType.SCANNER, savedWord.getSource());
        assertEquals("Detected in photo with 94% confidence", savedWord.getContext());
        assertEquals("food", savedWord.getTopic());
        assertNull(savedWord.getSourceId());

        assertEquals("apple", response.getWord());
        assertEquals("사과", response.getTranslation());
        assertEquals("ko", response.getLanguageCode());
        assertEquals("Korean", response.getLanguageName());
        assertEquals(SourceType.SCANNER, response.getSource());
        assertEquals("Detected in photo with 94% confidence", response.getSourceContext());
        assertEquals("food", response.getTopic());
    }

    @Test
    void saveWord_DuplicateWord_ThrowsDuplicateSavedWordException() {
        CreateWordRequest request = CreateWordRequest.builder()
                .word("さくら")
                .translation("cherry blossom")
                .languageCode("ja")
                .source(SourceType.MANUAL)
                .build();

        when(profileRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(savedWordRepository.existsByUserIdAndWordAndLanguageCode(any(), any(), any()))
                .thenReturn(true);

        DuplicateSavedWordException exception = assertThrows(
                DuplicateSavedWordException.class,
                () -> savedWordService.saveWord("test@example.com", request));
        assertEquals("Word already saved", exception.getMessage());
        verify(savedWordRepository, never()).save(any(SavedWord.class));
    }

    @Test
    void getUserWords_WithLanguageFilter() {
        Page<SavedWord> wordPage = new PageImpl<>(List.of(testWord));

        when(profileRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(savedWordRepository.findByUserIdAndLanguageCode(any(), eq("ja"), any(Pageable.class)))
                .thenReturn(wordPage);
        when(languageRepository.findById("ja"))
                .thenReturn(Optional.of(testLanguage));

        Page<SavedWordResponse> result = savedWordService.getUserWords(
                "test@example.com", "ja", "newest", 0, 50);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("さくら", result.getContent().get(0).getWord());
    }

    @Test
    void updateWord_Success() {
        UpdateWordRequest request = new UpdateWordRequest();
        request.setTranslation("sakura flower");
        request.setTopic("greetings");

        when(profileRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(savedWordRepository.findByIdAndUserId(testWord.getId(), testUser.getId()))
                .thenReturn(Optional.of(testWord));
        when(savedWordRepository.save(any(SavedWord.class)))
                .thenReturn(testWord);
        when(languageRepository.findById("ja"))
                .thenReturn(Optional.of(testLanguage));

        SavedWordResponse response = savedWordService.updateWord(
                "test@example.com", testWord.getId(), request);

        assertNotNull(response);
        assertEquals("greetings", testWord.getTopic());
        assertEquals("greetings", response.getTopic());
        verify(savedWordRepository).save(any(SavedWord.class));
    }

    @Test
    void deleteWord_Success() {
        when(profileRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(savedWordRepository.findByIdAndUserId(testWord.getId(), testUser.getId()))
                .thenReturn(Optional.of(testWord));

        savedWordService.deleteWord("test@example.com", testWord.getId());

        verify(savedWordRepository).delete(testWord);
    }
}
