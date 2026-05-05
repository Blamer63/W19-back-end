package com.example.demo.service;

import com.example.demo.dto.CreateWordRequest;
import com.example.demo.dto.SavedWordResponse;
import com.example.demo.dto.UpdateWordRequest;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.SavedWord;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.SavedWordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavedWordService {

    private final SavedWordRepository savedWordRepository;
    private final ProfileRepository profileRepository;
    private final LanguageRepository languageRepository;

    @Transactional
    public SavedWordResponse saveWord(String userEmail, CreateWordRequest request) {
        Profile user = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if word already exists
        if (savedWordRepository.existsByUserIdAndWordAndLanguageCode(
                user.getId(), request.getWord(), request.getLanguageCode())) {
            throw new RuntimeException("Word already saved");
        }

        SavedWord savedWord = SavedWord.builder()
                .user(user)
                .word(request.getWord())
                .translation(request.getTranslation())
                .languageCode(request.getLanguageCode())
                .source(request.getSource())
                .sourceId(request.getSourceId())
                .context(request.getSourceContext())
                .build();

        SavedWord saved = savedWordRepository.save(savedWord);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<SavedWordResponse> getUserWords(
            String userEmail,
            String languageCode,
            String sortBy,
            int page,
            int size) {

        Profile user = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate and cap page size
        size = Math.min(size, 100);

        // Create pageable with sorting
        Pageable pageable = createPageable(sortBy, page, size);

        Page<SavedWord> words;
        if (languageCode != null && !languageCode.isEmpty()) {
            words = savedWordRepository.findByUserIdAndLanguageCode(
                    user.getId(), languageCode, pageable);
        } else {
            words = savedWordRepository.findByUserId(user.getId(), pageable);
        }

        return words.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public SavedWordResponse getWord(String userEmail, UUID wordId) {
        Profile user = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SavedWord word = savedWordRepository.findByIdAndUserId(wordId, user.getId())
                .orElseThrow(() -> new RuntimeException("Word not found"));

        return mapToResponse(word);
    }

    @Transactional
    public SavedWordResponse updateWord(
            String userEmail,
            UUID wordId,
            UpdateWordRequest request) {

        Profile user = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SavedWord word = savedWordRepository.findByIdAndUserId(wordId, user.getId())
                .orElseThrow(() -> new RuntimeException("Word not found"));

        if (request.getTranslation() != null) {
            word.setTranslation(request.getTranslation());
        }
        if (request.getSourceContext() != null) {
            word.setContext(request.getSourceContext());
        }

        SavedWord updated = savedWordRepository.save(word);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteWord(String userEmail, UUID wordId) {
        Profile user = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SavedWord word = savedWordRepository.findByIdAndUserId(wordId, user.getId())
                .orElseThrow(() -> new RuntimeException("Word not found"));

        savedWordRepository.delete(word);
    }

    private Pageable createPageable(String sortBy, int page, int size) {
        Sort sort = switch (sortBy != null ? sortBy : "newest") {
            case "mastery_high" -> Sort.by(Sort.Direction.DESC, "masteryLevel");
            case "mastery_low" -> Sort.by(Sort.Direction.ASC, "masteryLevel");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        return PageRequest.of(page, size, sort);
    }

    private SavedWordResponse mapToResponse(SavedWord word) {
        Language language = languageRepository.findById(word.getLanguageCode())
                .orElse(null);

        return SavedWordResponse.builder()
                .id(word.getId())
                .word(word.getWord())
                .translation(word.getTranslation())
                .languageCode(word.getLanguageCode())
                .languageName(language != null ? language.getName() : "Unknown")
                .languageFlag(language != null ? language.getFlagEmoji() : "🌍")
                .source(word.getSource())
                .sourceId(word.getSourceId())
                .sourceContext(word.getContext())
                .masteryLevel(word.getMasteryLevel())
                .nextReview(word.getNextReview())
                .createdAt(word.getCreatedAt())
                .build();
    }
}
