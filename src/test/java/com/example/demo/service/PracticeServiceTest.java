package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PracticeServiceTest {

    @Mock
    private PracticeSessionRepository sessionRepository;

    @Mock
    private PracticeResultRepository resultRepository;

    @Mock
    private SavedWordRepository savedWordRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private LanguageRepository languageRepository;

    @InjectMocks
    private PracticeService practiceService;

    private Profile testUser;
    private SavedWord testWord;
    private PracticeSession testSession;

    @BeforeEach
    void setUp() {
        testUser = new Profile();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");

        testWord = SavedWord.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .word("test")
                .translation("テスト")
                .languageCode("ja")
                .masteryLevel(50)
                .build();

        testSession = PracticeSession.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .startedAt(Instant.now())
                .results(new ArrayList<>())
                .build();
    }

    @Test
    void startSession_InvalidSize_ThrowsException() {
        StartSessionRequest request = new StartSessionRequest();
        request.setSessionSize(20); // Invalid size

        when(profileRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));

        assertThrows(RuntimeException.class, () -> practiceService.startSession("test@example.com", request));
    }

    @Test
    void startSession_NotEnoughWords_ThrowsException() {
        StartSessionRequest request = new StartSessionRequest();
        request.setSessionSize(10);

        when(profileRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(savedWordRepository.findByUserId(any(), any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        assertThrows(RuntimeException.class, () -> practiceService.startSession("test@example.com", request));
    }

    @Test
    void submitResult_UpdatesMastery() {
        SubmitResultRequest request = new SubmitResultRequest();
        request.setWordId(testWord.getId());
        request.setIsCorrect(true);
        request.setResponseTimeMs(2500);

        when(profileRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(sessionRepository.findByIdAndUserId(testSession.getId(), testUser.getId()))
                .thenReturn(Optional.of(testSession));
        when(resultRepository.existsBySessionIdAndWordId(any(), any()))
                .thenReturn(false);
        when(savedWordRepository.findByIdAndUserId(testWord.getId(), testUser.getId()))
                .thenReturn(Optional.of(testWord));
        when(savedWordRepository.save(any(SavedWord.class)))
                .thenReturn(testWord);
        when(resultRepository.save(any(PracticeResult.class)))
                .thenReturn(new PracticeResult());

        SubmitResultResponse response = practiceService.submitResult(
                "test@example.com", testSession.getId(), request);

        assertNotNull(response);
        assertEquals(testWord.getId(), response.getWordId());
        assertTrue(response.getIsCorrect());
        verify(savedWordRepository).save(any(SavedWord.class));
        verify(resultRepository).save(any(PracticeResult.class));
    }

    @Test
    void submitResult_DuplicateSubmission_ThrowsException() {
        SubmitResultRequest request = new SubmitResultRequest();
        request.setWordId(testWord.getId());
        request.setIsCorrect(true);

        when(profileRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(sessionRepository.findByIdAndUserId(testSession.getId(), testUser.getId()))
                .thenReturn(Optional.of(testSession));
        when(resultRepository.existsBySessionIdAndWordId(testSession.getId(), testWord.getId()))
                .thenReturn(true);

        assertThrows(RuntimeException.class,
                () -> practiceService.submitResult("test@example.com", testSession.getId(), request));
    }

    @Test
    void completeSession_CalculatesStatsCorrectly() {
        PracticeResult result1 = PracticeResult.builder()
                .word(testWord)
                .isCorrect(true)
                .build();
        PracticeResult result2 = PracticeResult.builder()
                .word(testWord)
                .isCorrect(false)
                .build();

        testSession.getResults().add(result1);
        testSession.getResults().add(result2);

        when(profileRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(sessionRepository.findByIdAndUserId(testSession.getId(), testUser.getId()))
                .thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(PracticeSession.class)))
                .thenReturn(testSession);

        CompleteSessionResponse response = practiceService.completeSession(
                "test@example.com", testSession.getId());

        assertNotNull(response);
        assertEquals(2, response.getWordsPracticed());
        assertEquals(1, response.getCorrectCount());
        assertEquals(50, response.getAccuracy());
        assertNotNull(response.getDurationSeconds());
    }
}
