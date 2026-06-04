package com.example.demo.service;

import com.example.demo.dto.BoundingBoxDTO;
import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.enums.ProficiencyLevel;
import com.example.demo.enums.ScanMode;
import com.example.demo.enums.ScannerTranslationSource;
import com.example.demo.exception.ObjectDetectionUnavailableException;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.ScannerTranslationCacheRepository;
import com.example.demo.service.scanner.VisionDetection;
import com.example.demo.service.scanner.VisionServiceClient;
import com.example.demo.service.translation.TranslationClient;
import com.example.demo.service.translation.TranslationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectDetectionServiceTest {

    @Mock private VisionServiceClient visionServiceClient;
    @Mock private ProfileRepository profileRepository;
    @Mock private TranslationClient translationClient;
    @Mock private ScannerTranslationCacheRepository scannerTranslationCacheRepository;

    private ObjectDetectionService objectDetectionService;

    @BeforeEach
    void setUp() {
        ScannerVocabularyService scannerVocabularyService =
                new ScannerVocabularyService(translationClient, scannerTranslationCacheRepository);
        objectDetectionService = new ObjectDetectionService(
                visionServiceClient,
                profileRepository,
                scannerVocabularyService);
        ReflectionTestUtils.setField(objectDetectionService, "supportedTaxonomyLanguages", "en,es,fr,ja");
        objectDetectionService.parseSupportedTaxonomyLanguages();
    }

    @Test
    void detectUsesTaxonomyTranslationForSupportedLanguage() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "es");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("es", List.of(visionDetection("apple", "manzana", 0.08d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("apple");
        assertThat(detectedObjects.get(0).getConfidence()).isEqualTo(0.08d);
        assertThat(detectedObjects.get(0).getNativeWord()).isEqualTo("apple");
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("manzana");
        assertThat(detectedObjects.get(0).getLanguageCode()).isEqualTo("es");
        assertThat(detectedObjects.get(0).getTranslationSource()).isEqualTo(ScannerTranslationSource.TAXONOMY);
        verify(translationClient, never()).translate(any(), any(), any());
    }

    @Test
    void detectTargetsLearningLanguageAndKeepsNativeWordInUserNativeLanguage() throws IOException {
        Profile profile = profileWithNativeAndLearningLanguage("test@example.com", "es", "ja");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("ja", List.of(visionDetection("apple", "\u308A\u3093\u3054", 0.08d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("apple");
        assertThat(detectedObjects.get(0).getNativeWord()).isEqualTo("manzana");
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("\u308A\u3093\u3054");
        assertThat(detectedObjects.get(0).getLanguageCode()).isEqualTo("ja");
        assertThat(detectedObjects.get(0).getTranslationSource()).isEqualTo(ScannerTranslationSource.TAXONOMY);
        verify(visionServiceClient).analyze(anyString(), eq("ja"), eq(ScanMode.PRECISION));
        verify(translationClient, never()).translate(any(), any(), any());
    }

    @Test
    void detectUsesVocabularyForUnsupportedLanguage() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("ko", List.of(visionDetection("apple", "apple", 0.08d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("사과");
        assertThat(detectedObjects.get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(detectedObjects.get(0).getTranslationSource()).isEqualTo(ScannerTranslationSource.DICTIONARY);
        verify(translationClient, never()).translate(any(), any(), any());
    }

    @Test
    void detectDeduplicatesNormalizedLabelsAndKeepsHighestConfidence() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("ko", List.of(
                visionDetection("chair", "chair", 0.07d),
                visionDetection(" Chair ", "chair", 0.11d),
                visionDetection("CHAIR", "chair", 0.06d),
                visionDetection("apple", "apple", 0.09d),
                visionDetection("banana", "banana", 0.08d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(2);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("chair");
        assertThat(detectedObjects.get(0).getConfidence()).isEqualTo(0.11d);
        assertThat(detectedObjects.get(1).getLabel()).isEqualTo("apple");
        assertThat(detectedObjects.get(1).getConfidence()).isEqualTo(0.09d);
        assertThat(detectedObjects)
                .extracting(DetectedObjectDTO::getLabel)
                .doesNotContain("banana");
        verify(translationClient, never()).translate(any(), any(), any());
    }

    @Test
    void detectFiltersUnknownObjectDetections() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "es");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("es", List.of(
                visionDetection("unknown object", "unknown object", 0.0d),
                visionDetection(" UNKNOWN OBJECT ", "unknown object", 0.09d),
                visionDetection("lamp", "lampara", 0.055d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("lamp");
        assertThat(detectedObjects.get(0).getConfidence()).isEqualTo(0.055d);
    }

    @Test
    void detectAcceptsSiglipScaleConfidenceScores() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "es");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("es", List.of(visionDetection("apple", "manzana", 0.05d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("apple");
        assertThat(detectedObjects.get(0).getConfidence()).isEqualTo(0.05d);
    }

    @Test
    void detectMapsOptionalBoundingBox() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "es");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        BoundingBoxDTO box = BoundingBoxDTO.builder()
                .x(0.25d)
                .y(0.30d)
                .width(0.40d)
                .height(0.50d)
                .build();
        whenVisionReturns("es", List.of(visionDetection("apple", "manzana", 0.08d, box)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getBox()).isNotNull();
        assertThat(detectedObjects.get(0).getBox().getX()).isEqualTo(0.25d);
        assertThat(detectedObjects.get(0).getBox().getY()).isEqualTo(0.30d);
        assertThat(detectedObjects.get(0).getBox().getWidth()).isEqualTo(0.40d);
        assertThat(detectedObjects.get(0).getBox().getHeight()).isEqualTo(0.50d);
    }

    @Test
    void detectSortsByConfidenceAndLimitsResultsToTwoWithoutPostFilteringLowVisionScores() throws IOException {
        Profile profile = Profile.builder()
                .email("test@example.com")
                .languages(new ArrayList<>())
                .build();
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("en", List.of(
                visionDetection("object_00", "object 00", 0.050d),
                visionDetection("object_01", "object 01", 0.060d),
                visionDetection("object_02", "object 02", 0.070d),
                visionDetection("object_03", "object 03", 0.080d),
                visionDetection("object_04", "object 04", 0.090d),
                visionDetection("object_05", "object 05", 0.100d),
                visionDetection("object_06", "object 06", 0.110d),
                visionDetection("object_07", "object 07", 0.120d),
                visionDetection("object_08", "object 08", 0.130d),
                visionDetection("object_09", "object 09", 0.140d),
                visionDetection("object_10", "object 10", 0.150d),
                visionDetection("object_11", "object 11", 0.160d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(2);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("object 11");
        assertThat(detectedObjects.get(0).getConfidence()).isEqualTo(0.160d);
        assertThat(detectedObjects.get(1).getLabel()).isEqualTo("object 10");
        assertThat(detectedObjects.get(1).getConfidence()).isEqualTo(0.150d);
        assertThat(detectedObjects)
                .extracting(DetectedObjectDTO::getLabel)
                .doesNotContain("object 00", "object 01", "object 09");
        verify(translationClient, never()).translate(any(), any(), any());
    }

    @Test
    void detectSceneModeSortsByConfidenceAndLimitsResultsToFour() throws IOException {
        Profile profile = Profile.builder()
                .email("test@example.com")
                .languages(new ArrayList<>())
                .build();
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("en", List.of(
                visionDetection("object_00", "object 00", 0.050d),
                visionDetection("object_01", "object 01", 0.060d),
                visionDetection("object_02", "object 02", 0.070d),
                visionDetection("object_03", "object 03", 0.080d),
                visionDetection("object_04", "object 04", 0.090d),
                visionDetection("object_05", "object 05", 0.100d)));

        List<DetectedObjectDTO> detectedObjects =
                objectDetectionService.detect(image(), "test@example.com", ScanMode.SCENE);

        assertThat(detectedObjects).hasSize(4);
        assertThat(detectedObjects)
                .extracting(DetectedObjectDTO::getLabel)
                .containsExactly("object 05", "object 04", "object 03", "object 02");
        verify(visionServiceClient).analyze(anyString(), eq("en"), eq(ScanMode.SCENE));
        verify(translationClient, never()).translate(any(), any(), any());
    }

    @Test
    void detectFallsBackToEnglishTaxonomyWhenProfileHasNoLearningLanguage() throws IOException {
        Profile profile = Profile.builder()
                .email("test@example.com")
                .languages(new ArrayList<>())
                .build();
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("en", List.of(visionDetection("bottle", "Bottle", 0.08d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("bottle");
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("Bottle");
        assertThat(detectedObjects.get(0).getLanguageCode()).isEqualTo("en");
        assertThat(detectedObjects.get(0).getTranslationSource()).isEqualTo(ScannerTranslationSource.TAXONOMY);
        verify(translationClient, never()).translate(any(), any(), any());
    }

    @Test
    void detectTranslatesUnknownLabelViaTranslationClientForUnsupportedLanguage() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("ko", List.of(visionDetection("backpack", "backpack", 0.09d)));
        when(scannerTranslationCacheRepository.findByLabelAndLanguageCode("backpack", "ko"))
                .thenReturn(Optional.empty());
        when(translationClient.translate("backpack", "en", "ko"))
                .thenReturn(TranslationResult.builder().translatedText("배낭").build());

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("backpack");
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("배낭");
        assertThat(detectedObjects.get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(detectedObjects.get(0).getTranslationSource()).isEqualTo(ScannerTranslationSource.TRANSLATION_API);
    }

    @Test
    void detectFallsBackToEnglishLabelWhenTranslationClientFails() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("ko", List.of(visionDetection("backpack", "backpack", 0.09d)));
        when(scannerTranslationCacheRepository.findByLabelAndLanguageCode("backpack", "ko"))
                .thenReturn(Optional.empty());
        when(translationClient.translate("backpack", "en", "ko"))
                .thenThrow(new RuntimeException("Translation service unavailable"));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("backpack");
        assertThat(detectedObjects.get(0).getTranslationSource()).isEqualTo(ScannerTranslationSource.FALLBACK);
    }

    @Test
    void detectReusesOneTranslationForRepeatedNormalizedLabel() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenVisionReturns("ko", List.of(
                visionDetection("backpack", "backpack", 0.09d),
                visionDetection(" BACKPACK ", "backpack", 0.08d)));
        when(scannerTranslationCacheRepository.findByLabelAndLanguageCode("backpack", "ko"))
                .thenReturn(Optional.empty());
        when(translationClient.translate("backpack", "en", "ko"))
                .thenReturn(TranslationResult.builder().translatedText("배낭").build());

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("backpack");
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("배낭");
        assertThat(detectedObjects.get(0).getTranslationSource()).isEqualTo(ScannerTranslationSource.TRANSLATION_API);
        verify(translationClient, times(1)).translate("backpack", "en", "ko");
    }

    @Test
    void detectThrowsUnavailableExceptionWhenVisionRequestFails() {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        when(visionServiceClient.analyze(anyString(), eq("ko"), eq(ScanMode.PRECISION)))
                .thenThrow(new ObjectDetectionUnavailableException("Object detection service unavailable"));

        assertThatThrownBy(() -> objectDetectionService.detect(image(), "test@example.com"))
                .isInstanceOf(ObjectDetectionUnavailableException.class)
                .hasMessage("Object detection service unavailable");
    }

    @Test
    void detectRejectsEmptyImage() {
        MockMultipartFile emptyImage = new MockMultipartFile("image", "scan.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> objectDetectionService.detect(emptyImage, "test@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Image must not be empty");
    }

    @Test
    void detectRejectsUnsupportedImageType() {
        MockMultipartFile textFile = new MockMultipartFile("image", "notes.txt", "text/plain", "not-an-image".getBytes());

        assertThatThrownBy(() -> objectDetectionService.detect(textFile, "test@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported image type. Allowed: jpeg, png, webp");
    }

    @Test
    void detectRejectsImagesOverFiveMegabytes() {
        byte[] imageBytes = new byte[(5 * 1024 * 1024) + 1];
        Arrays.fill(imageBytes, (byte) 1);
        MockMultipartFile largeImage = new MockMultipartFile("image", "large.jpg", "image/jpeg", imageBytes);

        assertThatThrownBy(() -> objectDetectionService.detect(largeImage, "test@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Image exceeds 5 MB limit");
    }

    private void whenVisionReturns(String languageCode, List<VisionDetection> detections) {
        when(visionServiceClient.analyze(anyString(), eq(languageCode), any(ScanMode.class))).thenReturn(detections);
    }

    private VisionDetection visionDetection(String canonicalLabel, String translatedLabel, double confidence) {
        return visionDetection(canonicalLabel, translatedLabel, confidence, null);
    }

    private VisionDetection visionDetection(
            String canonicalLabel,
            String translatedLabel,
            double confidence,
            BoundingBoxDTO box) {
        return VisionDetection.builder()
                .canonicalLabel(canonicalLabel)
                .translatedLabel(translatedLabel)
                .confidence(confidence)
                .box(box)
                .build();
    }

    private Profile profileWithLearningLanguage(String email, String languageCode) {
        return Profile.builder()
                .email(email)
                .languages(new ArrayList<>(List.of(userLanguage(languageCode, ProficiencyLevel.BEGINNER, true))))
                .build();
    }

    private Profile profileWithNativeAndLearningLanguage(String email, String nativeLanguageCode, String learningLanguageCode) {
        return Profile.builder()
                .email(email)
                .languages(new ArrayList<>(List.of(
                        userLanguage(nativeLanguageCode, ProficiencyLevel.NATIVE, false),
                        userLanguage(learningLanguageCode, ProficiencyLevel.BEGINNER, true))))
                .build();
    }

    private UserLanguage userLanguage(String languageCode, ProficiencyLevel proficiency, boolean isLearning) {
        Language language = Language.builder()
                .code(languageCode)
                .name(languageCode)
                .build();
        return UserLanguage.builder()
                .language(language)
                .proficiency(proficiency)
                .isLearning(isLearning)
                .build();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "scan.jpg", "image/jpeg", "image-bytes".getBytes());
    }
}
