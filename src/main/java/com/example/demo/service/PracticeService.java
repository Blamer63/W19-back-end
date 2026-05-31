package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PracticeService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeResultRepository resultRepository;
    private final SavedWordRepository savedWordRepository;
    private final ProfileRepository profileRepository;
    private final LanguageRepository languageRepository;

    @Transactional
    public StartSessionResponse startSession(String userEmail, StartSessionRequest request) {
        Profile user = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate session size
        if (!List.of(5, 10, 15).contains(request.getSessionSize())) {
            throw new IllegalArgumentException("Session size must be 5, 10, or 15");
        }

        // Get words for practice
        List<SavedWord> availableWords = getAvailableWords(user.getId(), request.getLanguageCode());

        if (availableWords.size() < request.getSessionSize()) {
            throw new IllegalArgumentException("Not enough saved words for requested session size");
        }

        // Select words using weighted random
        List<SavedWord> selectedWords = selectWordsForPractice(availableWords, request.getSessionSize());

        // Create session
        PracticeSession session = PracticeSession.builder()
                .user(user)
                .build();

        PracticeSession savedSession = sessionRepository.save(session);

        // Map to response
        List<PracticeWordDTO> wordDTOs = selectedWords.stream()
                .map(this::mapToPracticeWord)
                .collect(Collectors.toList());

        return StartSessionResponse.builder()
                .sessionId(savedSession.getId())
                .words(wordDTOs)
                .startedAt(savedSession.getStartedAt())
                .build();
    }

    @Transactional
    public SubmitResultResponse submitResult(
            String userEmail,
            UUID sessionId,
            SubmitResultRequest request) {

        Profile user = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        PracticeSession session = sessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getEndedAt() != null) {
            throw new RuntimeException("Session already completed");
        }

        // Check if result already submitted
        if (resultRepository.existsBySessionIdAndWordId(sessionId, request.getWordId())) {
            throw new RuntimeException("Result already submitted for this word in session");
        }

        SavedWord word = savedWordRepository.findByIdAndUserId(request.getWordId(), user.getId())
                .orElseThrow(() -> new RuntimeException("Word not found"));

        // Calculate new mastery
        int oldMastery = word.getMasteryLevel();
        int newMastery = calculateNewMastery(oldMastery, request.getIsCorrect());
        int masteryChange = newMastery - oldMastery;

        // Update word mastery
        word.setMasteryLevel(newMastery);
        savedWordRepository.save(word);

        // Save result
        PracticeResult result = PracticeResult.builder()
                .session(session)
                .word(word)
                .isCorrect(request.getIsCorrect())
                .responseTimeMs(request.getResponseTimeMs())
                .build();

        resultRepository.save(result);

        return SubmitResultResponse.builder()
                .wordId(word.getId())
                .isCorrect(request.getIsCorrect())
                .newMasteryLevel(newMastery)
                .oldMastery(oldMastery)
                .newMastery(newMastery)
                .masteryChange(masteryChange)
                .build();
    }

    @Transactional
    public CompleteSessionResponse completeSession(String userEmail, UUID sessionId) {
        Profile user = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        PracticeSession session = sessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getEndedAt() != null) {
            throw new RuntimeException("Session already completed");
        }

        // Calculate stats
        List<PracticeResult> results = session.getResults();
        int wordsPracticed = results.size();
        int correctCount = (int) results.stream().filter(PracticeResult::getIsCorrect).count();
        int accuracy = wordsPracticed > 0 ? (correctCount * 100 / wordsPracticed) : 0;

        // Calculate duration
        Instant endedAt = Instant.now();
        long durationSeconds = Duration.between(session.getStartedAt(), endedAt).getSeconds();

        // Update session
        session.setEndedAt(endedAt);
        session.setWordsPracticed(wordsPracticed);
        session.setCorrectCount(correctCount);
        sessionRepository.save(session);

        // Build result DTOs
        List<WordResultDTO> wordResults = results.stream()
                .map(this::mapToWordResult)
                .collect(Collectors.toList());

        return CompleteSessionResponse.builder()
                .sessionId(session.getId())
                .wordsPracticed(wordsPracticed)
                .correctCount(correctCount)
                .accuracy(accuracy)
                .durationSeconds((int) durationSeconds)
                .results(wordResults)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<PracticeSession> getPracticeHistory(String userEmail, int page, int size) {
        Profile user = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(page, size);
        return sessionRepository.findByUserIdOrderByStartedAtDesc(user.getId(), pageable);
    }

    // Mastery calculation algorithm from frontend contract
    private int calculateNewMastery(int currentMastery, boolean isCorrect) {
        if (isCorrect) {
            // Increase mastery (diminishing returns at high levels)
            int increase = Math.max(5, (100 - currentMastery) / 5);
            return Math.min(100, currentMastery + increase);
        } else {
            // Decrease mastery
            int decrease = Math.max(10, currentMastery / 4);
            return Math.max(0, currentMastery - decrease);
        }
    }

    // Weighted random selection from frontend contract
    private List<SavedWord> selectWordsForPractice(List<SavedWord> words, int count) {
        // Calculate weights: (100 - mastery_level)² + 10
        Map<SavedWord, Integer> weights = words.stream()
                .collect(Collectors.toMap(
                        word -> word,
                        word -> (int) Math.pow(100 - word.getMasteryLevel(), 2) + 10));

        return weightedRandomSelection(weights, count);
    }

    private List<SavedWord> weightedRandomSelection(Map<SavedWord, Integer> weights, int count) {
        List<SavedWord> selected = new ArrayList<>();
        List<SavedWord> available = new ArrayList<>(weights.keySet());
        Random random = new Random();

        for (int i = 0; i < count && !available.isEmpty(); i++) {
            int totalWeight = available.stream()
                    .mapToInt(weights::get)
                    .sum();

            int randomValue = random.nextInt(totalWeight);
            int currentSum = 0;

            for (SavedWord word : available) {
                currentSum += weights.get(word);
                if (randomValue < currentSum) {
                    selected.add(word);
                    available.remove(word);
                    break;
                }
            }
        }

        return selected;
    }

    private List<SavedWord> getAvailableWords(UUID userId, String languageCode) {
        if (languageCode != null && !languageCode.isEmpty()) {
            return savedWordRepository.findByUserIdAndLanguageCode(
                    userId, languageCode, Pageable.unpaged()).getContent();
        } else {
            return savedWordRepository.findByUserId(userId, Pageable.unpaged()).getContent();
        }
    }

    private PracticeWordDTO mapToPracticeWord(SavedWord word) {
        Language language = languageRepository.findById(word.getLanguageCode()).orElse(null);

        return PracticeWordDTO.builder()
                .id(word.getId())
                .word(word.getWord())
                .translation(word.getTranslation())
                .languageCode(word.getLanguageCode())
                .languageFlag(language != null ? language.getFlagEmoji() : "🌍")
                .masteryLevel(word.getMasteryLevel())
                .build();
    }

    private WordResultDTO mapToWordResult(PracticeResult result) {
        SavedWord word = result.getWord();

        // Get old mastery by reversing the calculation
        int newMastery = word.getMasteryLevel();
        int oldMastery = result.getIsCorrect()
                ? Math.max(0, newMastery - Math.max(5, (100 - newMastery) / 5))
                : Math.min(100, newMastery + Math.max(10, newMastery / 4));

        return WordResultDTO.builder()
                .wordId(word.getId())
                .word(word.getWord())
                .isCorrect(result.getIsCorrect())
                .oldMastery(oldMastery)
                .newMastery(newMastery)
                .build();
    }
}
