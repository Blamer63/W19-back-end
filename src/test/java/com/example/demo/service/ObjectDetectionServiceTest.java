package com.example.demo.service;

import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.exception.ObjectDetectionUnavailableException;
import com.example.demo.repository.ProfileRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectDetectionServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private ProfileRepository profileRepository;
    @Mock private TranslationClient translationClient;

    private ObjectDetectionService objectDetectionService;

    @BeforeEach
    void setUp() {
        objectDetectionService = new ObjectDetectionService(restTemplate, profileRepository, translationClient);
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
        // dictionary hit — translation API must not be called
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
        whenYoloReturns(List.of(new ObjectDetectionService.YoloLabel("bottle", 0.91d)));
        when(translationClient.translate("bottle", "en", "ko"))
                .thenReturn(TranslationResult.builder().translatedText("병").build());

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLabel()).isEqualTo("bottle");
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("병");
        assertThat(detectedObjects.get(0).getLanguageCode()).isEqualTo("ko");
    }

    @Test
    void detectFallsBackToEnglishLabelWhenTranslationClientFails() throws IOException {
        Profile profile = profileWithLearningLanguage("test@example.com", "ko");
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(profile));
        whenYoloReturns(List.of(new ObjectDetectionService.YoloLabel("bottle", 0.91d)));
        when(translationClient.translate("bottle", "en", "ko"))
                .thenThrow(new RuntimeException("Translation service unavailable"));

        List<DetectedObjectDTO> detectedObjects = objectDetectionService.detect(image(), "test@example.com");

        assertThat(detectedObjects).hasSize(1);
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("bottle");
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
