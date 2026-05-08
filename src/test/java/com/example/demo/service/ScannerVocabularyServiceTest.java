package com.example.demo.service;

import com.example.demo.enums.ScannerTranslationSource;
import com.example.demo.entity.ScannerTranslationCache;
import com.example.demo.repository.ScannerTranslationCacheRepository;
import com.example.demo.service.translation.TranslationClient;
import com.example.demo.service.translation.TranslationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ScannerVocabularyServiceTest {

    @Mock
    private TranslationClient translationClient;

    @Mock
    private ScannerTranslationCacheRepository scannerTranslationCacheRepository;

    private ScannerVocabularyService scannerVocabularyService;

    @BeforeEach
    void setUp() {
        scannerVocabularyService = new ScannerVocabularyService(translationClient, scannerTranslationCacheRepository);
    }

    @Test
    void resolveReturnsDictionaryMatchWithoutCallingTranslationApi() {
        ScannerVocabularyService.VocabularyMatch match = scannerVocabularyService.resolve("chair", "ko");

        assertThat(match.getLearningWord()).isEqualTo("\uC758\uC790");
        assertThat(match.getTranslationSource()).isEqualTo(ScannerTranslationSource.DICTIONARY);
        verify(translationClient, never()).translate("chair", "en", "ko");
        verify(scannerTranslationCacheRepository, never()).findByLabelAndLanguageCode("chair", "ko");
    }

    @Test
    void resolveUsesTranslationApiForUnknownNonEnglishLabel() {
        when(scannerTranslationCacheRepository.findByLabelAndLanguageCode("backpack", "ko"))
                .thenReturn(Optional.empty());
        when(translationClient.translate("backpack", "en", "ko"))
                .thenReturn(TranslationResult.builder().translatedText("\uBC30\uB0AD").build());

        ScannerVocabularyService.VocabularyMatch match = scannerVocabularyService.resolve("backpack", "ko");

        assertThat(match.getLearningWord()).isEqualTo("\uBC30\uB0AD");
        assertThat(match.getTranslationSource()).isEqualTo(ScannerTranslationSource.TRANSLATION_API);
        verify(scannerTranslationCacheRepository).save(any(ScannerTranslationCache.class));
    }

    @Test
    void resolveUsesCachedTranslationBeforeTranslationApi() {
        when(scannerTranslationCacheRepository.findByLabelAndLanguageCode("backpack", "ko"))
                .thenReturn(Optional.of(ScannerTranslationCache.builder()
                        .label("backpack")
                        .languageCode("ko")
                        .translatedText("\uBC30\uB0AD")
                        .build()));

        ScannerVocabularyService.VocabularyMatch match = scannerVocabularyService.resolve("backpack", "ko");

        assertThat(match.getLearningWord()).isEqualTo("\uBC30\uB0AD");
        assertThat(match.getTranslationSource()).isEqualTo(ScannerTranslationSource.TRANSLATION_CACHE);
        verify(translationClient, never()).translate("backpack", "en", "ko");
        verify(scannerTranslationCacheRepository, never()).save(any(ScannerTranslationCache.class));
    }

    @Test
    void resolveFallsBackToLabelWhenTranslationApiFails() {
        when(scannerTranslationCacheRepository.findByLabelAndLanguageCode("backpack", "ko"))
                .thenReturn(Optional.empty());
        when(translationClient.translate("backpack", "en", "ko"))
                .thenThrow(new RuntimeException("translation unavailable"));

        ScannerVocabularyService.VocabularyMatch match = scannerVocabularyService.resolve("backpack", "ko");

        assertThat(match.getLearningWord()).isEqualTo("backpack");
        assertThat(match.getTranslationSource()).isEqualTo(ScannerTranslationSource.FALLBACK);
        verify(scannerTranslationCacheRepository, never()).save(any(ScannerTranslationCache.class));
    }

    @Test
    void resolveFallsBackToLabelForEnglishTargetWithoutCallingTranslationApi() {
        ScannerVocabularyService.VocabularyMatch match = scannerVocabularyService.resolve("bottle", "en");

        assertThat(match.getLearningWord()).isEqualTo("bottle");
        assertThat(match.getTranslationSource()).isEqualTo(ScannerTranslationSource.FALLBACK);
        verify(translationClient, never()).translate("bottle", "en", "en");
    }
}
