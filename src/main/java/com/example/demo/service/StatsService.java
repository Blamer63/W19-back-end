package com.example.demo.service;

import com.example.demo.dto.LanguageStatDTO;
import com.example.demo.dto.LearningStatsResponse;
import com.example.demo.dto.MasteryDistributionDTO;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.SavedWord;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.SavedWordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final SavedWordRepository savedWordRepository;
    private final ProfileRepository profileRepository;
    private final LanguageRepository languageRepository;

    @Transactional(readOnly = true)
    public LearningStatsResponse getLearningStats(String userEmail) {
        Profile user = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<SavedWord> allWords = savedWordRepository.findByUserId(
                user.getId(), Pageable.unpaged()).getContent();

        if (allWords.isEmpty()) {
            return LearningStatsResponse.builder()
                    .totalWords(0)
                    .averageMastery(0)
                    .languages(List.of())
                    .masteryDistribution(MasteryDistributionDTO.builder()
                            .beginner(0)
                            .learning(0)
                            .familiar(0)
                            .mastered(0)
                            .build())
                    .build();
        }

        // Calculate total and average
        int totalWords = allWords.size();
        int averageMastery = (int) allWords.stream()
                .mapToInt(SavedWord::getMasteryLevel)
                .average()
                .orElse(0);

        // Group by language
        Map<String, List<SavedWord>> wordsByLanguage = allWords.stream()
                .collect(Collectors.groupingBy(SavedWord::getLanguageCode));

        List<LanguageStatDTO> languageStats = wordsByLanguage.entrySet().stream()
                .map(entry -> {
                    String langCode = entry.getKey();
                    List<SavedWord> words = entry.getValue();
                    Language language = languageRepository.findById(langCode).orElse(null);

                    int wordCount = words.size();
                    int avgMastery = (int) words.stream()
                            .mapToInt(SavedWord::getMasteryLevel)
                            .average()
                            .orElse(0);

                    return LanguageStatDTO.builder()
                            .code(langCode)
                            .name(language != null ? language.getName() : "Unknown")
                            .flag(language != null ? language.getFlagEmoji() : "🌍")
                            .wordCount(wordCount)
                            .averageMastery(avgMastery)
                            .build();
                })
                .collect(Collectors.toList());

        // Calculate mastery distribution
        MasteryDistributionDTO distribution = calculateMasteryDistribution(allWords);

        return LearningStatsResponse.builder()
                .totalWords(totalWords)
                .averageMastery(averageMastery)
                .languages(languageStats)
                .masteryDistribution(distribution)
                .build();
    }

    private MasteryDistributionDTO calculateMasteryDistribution(List<SavedWord> words) {
        int beginner = 0;
        int learning = 0;
        int familiar = 0;
        int mastered = 0;

        for (SavedWord word : words) {
            int mastery = word.getMasteryLevel();
            if (mastery <= 25) {
                beginner++;
            } else if (mastery <= 50) {
                learning++;
            } else if (mastery <= 75) {
                familiar++;
            } else {
                mastered++;
            }
        }

        return MasteryDistributionDTO.builder()
                .beginner(beginner)
                .learning(learning)
                .familiar(familiar)
                .mastered(mastered)
                .build();
    }
}
