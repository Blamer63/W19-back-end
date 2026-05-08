package com.example.demo.service;

import com.example.demo.dto.BoundingBoxDTO;
import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.exception.ObjectDetectionUnavailableException;
import com.example.demo.repository.ProfileRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ObjectDetectionService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SCAN_SIZE = 5L * 1024 * 1024;
    private static final int MAX_DETECTED_OBJECTS = 10;

    private static final String DEFAULT_LANGUAGE_CODE = "en";

    private final RestTemplate restTemplate;
    private final ProfileRepository profileRepository;
    private final ScannerVocabularyService scannerVocabularyService;

    @Value("${app.yolo.endpoint}")
    private String yoloEndpoint;

    @Value("${app.yolo.confidence-threshold}")
    private double confidenceThreshold;

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("Image must not be empty");
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType()))
            throw new IllegalArgumentException("Unsupported image type. Allowed: jpeg, png, webp");
        if (file.getSize() > MAX_SCAN_SIZE)
            throw new IllegalArgumentException("Image exceeds 5 MB limit");
    }

    @Transactional(readOnly = true)
    public List<DetectedObjectDTO> detect(MultipartFile image, String currentUserEmail) throws IOException {
        validateImage(image);
        String languageCode = resolveLearningLanguageCode(currentUserEmail);
        List<YoloLabel> labels = requestDetections(image);
        Map<String, ScannerVocabularyService.VocabularyMatch> vocabularyCache = new HashMap<>();

        return labels.stream()
                .filter(label -> label.getLabel() != null)
                .filter(label -> !normalizeLabel(label.getLabel()).isBlank())
                .filter(label -> label.getConfidence() >= confidenceThreshold)
                .collect(Collectors.toMap(
                        label -> normalizeLabel(label.getLabel()),
                        Function.identity(),
                        (existing, candidate) -> existing.getConfidence() >= candidate.getConfidence()
                                ? existing
                                : candidate))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(YoloLabel::getConfidence).reversed())
                .limit(MAX_DETECTED_OBJECTS)
                .map(label -> toDetectedObject(label, languageCode, vocabularyCache))
                .toList();
    }

    private List<YoloLabel> requestDetections(MultipartFile image) throws IOException {
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.parseMediaType(
                Optional.ofNullable(image.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE)));

        ByteArrayResource imageResource = new ByteArrayResource(image.getBytes()) {
            @Override
            public String getFilename() {
                return Optional.ofNullable(image.getOriginalFilename()).orElse("scan-image");
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new HttpEntity<>(imageResource, imageHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        try {
            ResponseEntity<List<YoloLabel>> response = restTemplate.exchange(
                    yoloEndpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {
                    });
            return Optional.ofNullable(response.getBody()).orElse(List.of());
        } catch (RestClientException ex) {
            throw new ObjectDetectionUnavailableException("Object detection service unavailable", ex);
        }
    }

    private String resolveLearningLanguageCode(String currentUserEmail) {
        Profile profile = profileRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return profile.getLanguages().stream()
                .filter(UserLanguage::isLearning)
                .map(UserLanguage::getLanguage)
                .filter(language -> language != null && language.getCode() != null)
                .map(language -> language.getCode().toLowerCase(Locale.ROOT))
                .findFirst()
                .orElse(DEFAULT_LANGUAGE_CODE);
    }

    private DetectedObjectDTO toDetectedObject(
            YoloLabel label,
            String languageCode,
            Map<String, ScannerVocabularyService.VocabularyMatch> vocabularyCache) {
        String normalizedLabel = normalizeLabel(label.getLabel());
        ScannerVocabularyService.VocabularyMatch vocabulary = vocabularyCache.computeIfAbsent(
                normalizedLabel + ":" + languageCode,
                ignored -> scannerVocabularyService.resolve(normalizedLabel, languageCode));

        return DetectedObjectDTO.builder()
                .label(normalizedLabel)
                .confidence(label.getConfidence())
                .nativeWord(normalizedLabel)
                .learningWord(vocabulary.getLearningWord())
                .languageCode(languageCode)
                .translationSource(vocabulary.getTranslationSource())
                .box(label.getBox())
                .build();
    }

    private String normalizeLabel(String label) {
        return label.trim()
                .replace('_', ' ')
                .toLowerCase(Locale.ROOT);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class YoloLabel {
        private String label;
        private double confidence;
        private BoundingBoxDTO box;

        YoloLabel(String label, double confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }
}
