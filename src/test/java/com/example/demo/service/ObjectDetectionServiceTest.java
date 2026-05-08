package com.example.demo.service;

import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.dto.BoundingBoxDTO;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.enums.ScannerTranslationSource;
import com.example.demo.exception.ObjectDetectionUnavailableException;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.ScannerTranslationCacheRepository;
import com.example.demo.service.translation.TranslationClient;
import com.example.demo.service.translation.TranslationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectDetectionServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private ProfileRepository profileRepository;
    @Mock private TranslationClient translationClient;
    @Mock private ScannerTranslationCacheRepository scannerTranslationCacheRepository;

    private ObjectDetectionService objectDetectionService;

    @BeforeEach
    void setUp() {
        ScannerVocabularyService scannerVocabularyService =
                new ScannerVocabularyService(translationClient, scannerTranslationCacheRepository);
        objectDetectionService = new ObjectDetectionService(restTemplate, profileRepository, scannerVocabularyService);
        ReflectionTestUtils.setField(objectDetectionService, "yoloEndpoint", "http://localhost:5001/detect");
        ReflectionTestUtils.setField(objectDetectionService, "confidenceThreshold", 0.60d);
    }

    // -------------------------------------------------------------------------
    // Dictionary hits — translation API must NOT be called
    // -------------------------------------------------------------------------

    @Test
    void detectFiltersLowConfidenceObjectsAndTranslatesToLearningLanguage() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenYoloReturns(List.of(
                new ObjectDetectionService.YoloLabel("apple", 0.94d),
                new ObjectDetectionService.YoloLabel("chair", 0.59d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("apple");
        assertThat(detectedObjects.get(0).getConfidence()).isEqualTo(0.94d);
        assertThat(detectedObjects.get(0).getNativeWord()).isEqualTo("apple");
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("사과"); // 사과
        assertThat(detectedObjects.get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(detectedObjects.get(0).getTranslationSource()).isEqualTo(ScannerTranslationSource.DICTIONARY);
        // dictionary hit — translation API must not be called
        verify(translationClient, never()).translate(any(), any(), any());
    }

    @Test
    void detectDeduplicatesNormalizedLabelsAndKeepsHighestConfidence() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenYoloReturns(List.of(
                new ObjectDetectionService.YoloLabel("chair", 0.72d),
                new ObjectDetectionService.YoloLabel(" Chair ", 0.91d),
                new ObjectDetectionService.YoloLabel("CHAIR", 0.68d),
                new ObjectDetectionService.YoloLabel("apple", 0.80d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(2);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("chair");
        assertThat(detectedObjects.get(0).getNativeWord()).isEqualTo("chair");
        assertThat(detectedObjects.get(0).getConfidence()).isEqualTo(0.91d);
        assertThat(detectedObjects.get(1).getLabel()).isEqualTo("apple");
        assertThat(detectedObjects.get(1).getConfidence()).isEqualTo(0.80d);
        verify(translationClient, never()).translate(any(), any(), any());
    }

    @Test
    void detectMapsOptionalBoundingBox() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        BoundingBoxDTO box = BoundingBoxDTO.builder()
                .x(0.25d)
                .y(0.30d)
                .width(0.40d)
                .height(0.50d)
                .build();
        whenYoloReturns(List.of(new ObjectDetectionService.YoloLabel("apple", 0.94d, box)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getBox()).isNotNull();
        assertThat(detectedObjects.get(0).getBox().getX()).isEqualTo(0.25d);
        assertThat(detectedObjects.get(0).getBox().getY()).isEqualTo(0.30d);
        assertThat(detectedObjects.get(0).getBox().getWidth()).isEqualTo(0.40d);
        assertThat(detectedObjects.get(0).getBox().getHeight()).isEqualTo(0.50d);
    }

    @Test
    void detectSortsByConfidenceAndLimitsResults() throws IOException {
        Profile profile = Profile.builder()
                .email("test@example.com")
                .languages(new ArrayList<>())
                .build();
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenYoloReturns(List.of(
                new ObjectDetectionService.YoloLabel("object_00", 0.60d),
                new ObjectDetectionService.YoloLabel("object_01", 0.61d),
                new ObjectDetectionService.YoloLabel("object_02", 0.62d),
                new ObjectDetectionService.YoloLabel("object_03", 0.63d),
                new ObjectDetectionService.YoloLabel("object_04", 0.64d),
                new ObjectDetectionService.YoloLabel("object_05", 0.65d),
                new ObjectDetectionService.YoloLabel("object_06", 0.66d),
                new ObjectDetectionService.YoloLabel("object_07", 0.67d),
                new ObjectDetectionService.YoloLabel("object_08", 0.68d),
                new ObjectDetectionService.YoloLabel("object_09", 0.69d),
                new ObjectDetectionService.YoloLabel("object_10", 0.70d),
                new ObjectDetectionService.YoloLabel("object_11", 0.71d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(10);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("object 11");
        assertThat(detectedObjects.get(0).getConfidence()).isEqualTo(0.71d);
        assertThat(detectedObjects.get(9).getLabel()).isEqualTo("object 02");
        assertThat(detectedObjects.get(9).getConfidence()).isEqualTo(0.62d);
        assertThat(detectedObjects)
                .extracting(DetectedObjectDTO::getLabel)
                .doesNotContain("object 00", "object 01");
        verify(translationClient, never()).translate(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // English fallback — translation API must NOT be called
    // -------------------------------------------------------------------------

    @Test
    void detectFallsBackToEnglishWhenProfileHasNoLearningLanguage() throws IOException {
        Profile profile = Profile.builder()
                .email("test@example.com")
                .languages(new ArrayList<>())
                .build();
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenYoloReturns(List.of(new ObjectDetectionService.YoloLabel("bottle", 0.91d)));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("bottle");
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("bottle");
        assertThat(detectedObjects.get(0).getLanguageCode()).isEqualTo("en");
        assertThat(detectedObjects.get(0).getTranslationSource()).isEqualTo(ScannerTranslationSource.FALLBACK);
        // language is "en" — translation API must not be called
        verify(translationClient, never()).translate(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Translation API fallback — label not in dictionary, non-English target
    // -------------------------------------------------------------------------

    @Test
    void detectTranslatesUnknownLabelViaTranslationClient() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenYoloReturns(List.of(new ObjectDetectionService.YoloLabel("backpack", 0.91d)));
        when(scannerTranslationCacheRepository.findByLabelAndLanguageCode("backpack", "ko"))
                .thenReturn(Optional.empty());
        when(translationClient.translate("backpack", "en", "ko"))
                .thenReturn(TranslationResult.builder().translatedText("병").build());

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("backpack");
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("병");
        assertThat(detectedObjects.get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(detectedObjects.get(0).getTranslationSource()).isEqualTo(ScannerTranslationSource.TRANSLATION_API);
    }

    @Test
    void detectFallsBackToEnglishLabelWhenTranslationClientFails() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenYoloReturns(List.of(new ObjectDetectionService.YoloLabel("backpack", 0.91d)));
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
        whenYoloReturns(List.of(
                new ObjectDetectionService.YoloLabel("backpack", 0.91d),
                new ObjectDetectionService.YoloLabel(" BACKPACK ", 0.88d)));
        when(scannerTranslationCacheRepository.findByLabelAndLanguageCode("backpack", "ko"))
                .thenReturn(Optional.empty());
        when(translationClient.translate("backpack", "en", "ko"))
                .thenReturn(TranslationResult.builder().translatedText("\uBC30\uB0AD").build());

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("backpack");
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("\uBC30\uB0AD");
        assertThat(detectedObjects.get(0).getTranslationSource()).isEqualTo(ScannerTranslationSource.TRANSLATION_API);
        verify(translationClient, times(1)).translate("backpack", "en", "ko");
    }

    // -------------------------------------------------------------------------
    // YOLO service failure
    // -------------------------------------------------------------------------

    @Test
    void detectThrowsUnavailableExceptionWhenYoloRequestFails() {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        when(restTemplate.exchange(
                eq("http://localhost:5001/detect"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<List<ObjectDetectionService.YoloLabel>>>any()))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> objectDetectionService.detect(image(), "test@example.com"))
                .isInstanceOf(ObjectDetectionUnavailableException.class)
                .hasMessage("Object detection service unavailable");
    }

    // -------------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void whenYoloReturns(List<ObjectDetectionService.YoloLabel> labels) {
        when(restTemplate.exchange(
                eq("http://localhost:5001/detect"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<List<ObjectDetectionService.YoloLabel>>>any()))
                .thenReturn(ResponseEntity.ok(labels));
    }

    private Profile profileWithLearningLanguage(String email, String languageCode) {
        Language language = Language.builder()
                .code(languageCode)
                .name(languageCode)
                .build();
        UserLanguage userLanguage = UserLanguage.builder()
                .language(language)
                .isLearning(true)
                .build();
        return Profile.builder()
                .email(email)
                .languages(new ArrayList<>(List.of(userLanguage)))
                .build();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "scan.jpg", "image/jpeg", "image-bytes".getBytes());
    }
}
