package com.example.demo.service;

import com.example.demo.enums.ScannerTranslationSource;
import com.example.demo.service.translation.TranslationClient;
import com.example.demo.service.translation.TranslationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScannerVocabularyServiceTest {

    @Mock
    private TranslationClient translationClient;

    private ScannerVocabularyService scannerVocabularyService;

    @BeforeEach
    void setUp() {
        scannerVocabularyService = new ScannerVocabularyService(translationClient);
    }

    @Test
    void resolveReturnsDictionaryMatchWithoutCallingTranslationApi() {
        ScannerVocabularyService.VocabularyMatch match = scannerVocabularyService.resolve("chair", "ko");

        assertThat(match.getLearningWord()).isEqualTo("\uC758\uC790");
        assertThat(match.getTranslationSource()).isEqualTo(ScannerTranslationSource.DICTIONARY);
        verify(translationClient, never()).translate("chair", "en", "ko");
    }

    @Test
    void resolveUsesTranslationApiForUnknownNonEnglishLabel() {
        when(translationClient.translate("backpack", "en", "ko"))
                .thenReturn(TranslationResult.builder().translatedText("\uBC30\uB0AD").build());

        ScannerVocabularyService.VocabularyMatch match = scannerVocabularyService.resolve("backpack", "ko");

        assertThat(match.getLearningWord()).isEqualTo("\uBC30\uB0AD");
        assertThat(match.getTranslationSource()).isEqualTo(ScannerTranslationSource.TRANSLATION_API);
    }

    @Test
    void resolveFallsBackToLabelWhenTranslationApiFails() {
        when(translationClient.translate("backpack", "en", "ko"))
                .thenThrow(new RuntimeException("translation unavailable"));

        ScannerVocabularyService.VocabularyMatch match = scannerVocabularyService.resolve("backpack", "ko");

        assertThat(match.getLearningWord()).isEqualTo("backpack");
        assertThat(match.getTranslationSource()).isEqualTo(ScannerTranslationSource.FALLBACK);
    }

    @Test
    void resolveFallsBackToLabelForEnglishTargetWithoutCallingTranslationApi() {
        ScannerVocabularyService.VocabularyMatch match = scannerVocabularyService.resolve("bottle", "en");

        assertThat(match.getLearningWord()).isEqualTo("bottle");
        assertThat(match.getTranslationSource()).isEqualTo(ScannerTranslationSource.FALLBACK);
        verify(translationClient, never()).translate("bottle", "en", "en");
    }
}
