package com.example.demo.service;

import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.exception.ObjectDetectionUnavailableException;
import com.example.demo.repository.ProfileRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectDetectionServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ProfileRepository profileRepository;

    private ObjectDetectionService objectDetectionService;

    @BeforeEach
    void setUp() {
        objectDetectionService = new ObjectDetectionService(restTemplate, profileRepository);
        ReflectionTestUtils.setField(objectDetectionService, "yoloEndpoint", "http://localhost:5001/detect");
        ReflectionTestUtils.setField(objectDetectionService, "confidenceThreshold", 0.60d);
    }

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
        assertThat(detectedObjects.get(0).getLearningWord()).isEqualTo("\uC0AC\uACFC");
        assertThat(detectedObjects.get(0).getLanguageCode()).isEqualTo("ko");
    }

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
    }

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
