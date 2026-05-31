package com.example.demo.service;

import com.example.demo.dto.PostTranslationResponse;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostTranslation;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.PostTranslationRepository;
import com.example.demo.service.translation.TranslationClient;
import com.example.demo.service.translation.TranslationProviderException;
import com.example.demo.service.translation.TranslationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostTranslationRepository postTranslationRepository;

    @Mock
    private LanguageRepository languageRepository;

    @Mock
    private TranslationClient translationClient;

    @InjectMocks
    private TranslationService translationService;

    private UUID postId;
    private Post post;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        post = Post.builder()
                .content("Hello world")
                .originalLanguage("en")
                .build();
        post.setId(postId);
    }

    @Test
    void getTranslation_CacheHit_DoesNotCallProvider() {
        PostTranslation cached = PostTranslation.builder()
                .post(post)
                .languageCode("ko")
                .translatedContent("안녕하세요")
                .build();

        when(languageRepository.existsById("ko")).thenReturn(true);
        when(postTranslationRepository.findByPostIdAndLanguageCode(postId, "ko"))
                .thenReturn(Optional.of(cached));

        PostTranslationResponse response = translationService.getTranslation(postId, " KO ");

        assertEquals("ko", response.getLanguageCode());
        assertEquals("안녕하세요", response.getTranslatedContent());
        verifyNoInteractions(postRepository, translationClient);
    }

    @Test
    void getTranslation_CacheMiss_CallsProviderAndSavesResult() {
        when(languageRepository.existsById("ko")).thenReturn(true);
        when(postTranslationRepository.findByPostIdAndLanguageCode(postId, "ko"))
                .thenReturn(Optional.empty());
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(translationClient.translate("Hello world", "en", "ko"))
                .thenReturn(TranslationResult.builder()
                        .translatedText("안녕하세요")
                        .detectedSourceLanguage("en")
                        .build());
        when(postTranslationRepository.save(any(PostTranslation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PostTranslationResponse response = translationService.getTranslation(postId, "ko");

        assertEquals("ko", response.getLanguageCode());
        assertEquals("안녕하세요", response.getTranslatedContent());
        verify(postTranslationRepository).save(argThat(translation ->
                translation.getPost() == post
                        && translation.getLanguageCode().equals("ko")
                        && translation.getTranslatedContent().equals("안녕하세요")));
    }

    @Test
    void getTranslation_SameSourceAndTarget_ReturnsOriginalContentWithoutProvider() {
        when(languageRepository.existsById("en")).thenReturn(true);
        when(postTranslationRepository.findByPostIdAndLanguageCode(postId, "en"))
                .thenReturn(Optional.empty());
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postTranslationRepository.save(any(PostTranslation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PostTranslationResponse response = translationService.getTranslation(postId, "en");

        assertEquals("en", response.getLanguageCode());
        assertEquals("Hello world", response.getTranslatedContent());
        verifyNoInteractions(translationClient);
    }

    @Test
    void getTranslation_InvalidTargetLanguage_ThrowsIllegalArgumentException() {
        when(languageRepository.existsById("xx")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> translationService.getTranslation(postId, "xx"));

        assertEquals("Unsupported target language: xx", ex.getMessage());
        verifyNoInteractions(postRepository, postTranslationRepository, translationClient);
    }

    @Test
    void translateText_AllowsCanonicalFallbackLanguagesWhenSeeded() {
        Stream.of("pt", "ko", "vi").forEach(languageCode -> {
            when(languageRepository.existsById(languageCode)).thenReturn(true);
            when(translationClient.translate("Hello world", "en", languageCode))
                    .thenReturn(TranslationResult.builder()
                            .translatedText("translated-" + languageCode)
                            .detectedSourceLanguage("en")
                            .build());

            assertEquals(
                    "translated-" + languageCode,
                    translationService.translateText("Hello world", "en", languageCode).getTranslatedText());
        });
    }

    @Test
    void getTranslation_MissingPost_ThrowsResourceNotFoundException() {
        when(languageRepository.existsById("ko")).thenReturn(true);
        when(postTranslationRepository.findByPostIdAndLanguageCode(postId, "ko"))
                .thenReturn(Optional.empty());
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> translationService.getTranslation(postId, "ko"));
        verifyNoInteractions(translationClient);
    }

    @Test
    void getTranslation_ProviderFailure_DoesNotCacheFailure() {
        when(languageRepository.existsById("ko")).thenReturn(true);
        when(postTranslationRepository.findByPostIdAndLanguageCode(postId, "ko"))
                .thenReturn(Optional.empty());
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(translationClient.translate("Hello world", "en", "ko"))
                .thenThrow(new TranslationProviderException("Translation provider is unavailable"));

        assertThrows(TranslationProviderException.class, () -> translationService.getTranslation(postId, "ko"));
        verify(postTranslationRepository, never()).save(any());
    }
}
