package com.example.demo.service;

import com.example.demo.dto.PostTranslationResponse;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostTranslation;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.PostTranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TranslationService {

    private final PostRepository postRepository;
    private final PostTranslationRepository postTranslationRepository;

    @Transactional
    public PostTranslationResponse getTranslation(UUID postId, String targetLanguage) {
        // Check cache first
        return postTranslationRepository.findByPostIdAndLanguageCode(postId, targetLanguage)
                .map(this::mapToResponse)
                .orElseGet(() -> createTranslation(postId, targetLanguage));
    }

    private PostTranslationResponse createTranslation(UUID postId, String targetLanguage) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Mock translation logic for now
        String translatedText = "[Translated to " + targetLanguage + "]: " + post.getContent();

        PostTranslation translation = PostTranslation.builder()
                .post(post)
                .languageCode(targetLanguage)
                .translatedContent(translatedText)
                .build();

        PostTranslation saved = postTranslationRepository.save(translation);
        return mapToResponse(saved);
    }

    private PostTranslationResponse mapToResponse(PostTranslation translation) {
        return PostTranslationResponse.builder()
                .languageCode(translation.getLanguageCode())
                .translatedContent(translation.getTranslatedContent())
                .build();
    }
}
