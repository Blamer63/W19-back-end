package com.example.demo.service;

import com.example.demo.dto.PostTranslationResponse;
import com.example.demo.dto.TextTranslationResponse;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostTranslation;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.PostTranslationRepository;
import com.example.demo.service.translation.TranslationClient;
import com.example.demo.service.translation.TranslationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TranslationService {

    private final PostRepository postRepository;
    private final PostTranslationRepository postTranslationRepository;
    private final LanguageRepository languageRepository;
    private final TranslationClient translationClient;

    @Transactional
    public PostTranslationResponse getTranslation(UUID postId, String targetLanguage) {
        String normalizedTargetLanguage = normalizeLanguageCode(targetLanguage);
        validateTargetLanguage(normalizedTargetLanguage);

        // Check cache first
        return postTranslationRepository.findByPostIdAndLanguageCode(postId, normalizedTargetLanguage)
                .map(this::mapToResponse)
                .orElseGet(() -> createTranslation(postId, normalizedTargetLanguage));
    }

    private PostTranslationResponse createTranslation(UUID postId, String targetLanguage) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        String sourceLanguage = normalizeNullableLanguageCode(post.getOriginalLanguage());
        String translatedText;

        if (!StringUtils.hasText(post.getContent())) {
            throw new IllegalArgumentException("Post content is empty");
        } else if (targetLanguage.equals(sourceLanguage)) {
            translatedText = post.getContent();
        } else {
            TranslationResult result = translationClient.translate(post.getContent(), sourceLanguage, targetLanguage);
            translatedText = result.getTranslatedText();
        }

        PostTranslation translation = PostTranslation.builder()
                .post(post)
                .languageCode(targetLanguage)
                .translatedContent(translatedText)
                .build();

        PostTranslation saved = postTranslationRepository.save(translation);
        return mapToResponse(saved);
    }

    public TextTranslationResponse translateText(String text, String sourceLanguage, String targetLanguage) {
        String normalizedTarget = normalizeLanguageCode(targetLanguage);
        validateTargetLanguage(normalizedTarget);
        String normalizedSource = normalizeNullableLanguageCode(sourceLanguage);
        if (normalizedTarget.equals(normalizedSource)) {
            return TextTranslationResponse.builder().translatedText(text).build();
        }
        TranslationResult result = translationClient.translate(text, normalizedSource, normalizedTarget);
        return TextTranslationResponse.builder().translatedText(result.getTranslatedText()).build();
    }

    private PostTranslationResponse mapToResponse(PostTranslation translation) {
        return PostTranslationResponse.builder()
                .languageCode(translation.getLanguageCode())
                .translatedContent(translation.getTranslatedContent())
                .build();
    }

    private String normalizeLanguageCode(String languageCode) {
        if (!StringUtils.hasText(languageCode)) {
            throw new IllegalArgumentException("target_language is required");
        }
        return languageCode.trim().toLowerCase();
    }

    private String normalizeNullableLanguageCode(String languageCode) {
        return StringUtils.hasText(languageCode) ? languageCode.trim().toLowerCase() : null;
    }

    private void validateTargetLanguage(String targetLanguage) {
        if (!languageRepository.existsById(targetLanguage)) {
            throw new IllegalArgumentException("Unsupported target language: " + targetLanguage);
        }
    }
}
