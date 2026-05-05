package com.example.demo.service;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ObjectDetectionService {

    private static final String DEFAULT_LANGUAGE_CODE = "en";
    private static final Map<String, Map<String, String>> OBJECT_DICTIONARY = Map.of(
            "apple", Map.of("ko", "\uC0AC\uACFC", "ja", "\u308A\u3093\u3054", "vi", "t\u00E1o",
                    "zh", "\u82F9\u679C", "es", "manzana", "fr", "pomme"),
            "chair", Map.of("ko", "\uC758\uC790", "ja", "\u6905\u5B50", "vi", "gh\u1EBF",
                    "zh", "\u6905\u5B50", "es", "silla", "fr", "chaise"),
            "dog", Map.of("ko", "\uAC1C", "ja", "\u72AC", "vi", "ch\u00F3",
                    "zh", "\u72D7", "es", "perro", "fr", "chien"),
            "laptop", Map.of("ko", "\uB178\uD2B8\uBD81", "ja", "\u30CE\u30FC\u30C8\u30D1\u30BD\u30B3\u30F3",
                    "vi", "m\u00E1y t\u00EDnh x\u00E1ch tay", "zh", "\u7B14\u8BB0\u672C\u7535\u8111",
                    "es", "portatil", "fr", "ordinateur portable"),
            "book", Map.of("ko", "\uCC45", "ja", "\u672C", "vi", "s\u00E1ch",
                    "zh", "\u4E66", "es", "libro", "fr", "livre"));

    private final RestTemplate restTemplate;
    private final ProfileRepository profileRepository;

    @Value("${app.yolo.endpoint}")
    private String yoloEndpoint;

    @Value("${app.yolo.confidence-threshold}")
    private double confidenceThreshold;

    @Transactional(readOnly = true)
    public List<DetectedObjectDTO> detect(MultipartFile image, String currentUserEmail) throws IOException {
        String languageCode = resolveLearningLanguageCode(currentUserEmail);
        List<YoloLabel> labels = requestDetections(image);

        return labels.stream()
                .filter(label -> label.getLabel() != null)
                .filter(label -> label.getConfidence() >= confidenceThreshold)
                .map(label -> toDetectedObject(label, languageCode))
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

    private DetectedObjectDTO toDetectedObject(YoloLabel label, String languageCode) {
        String normalizedLabel = label.getLabel().toLowerCase(Locale.ROOT);
        String learningWord = OBJECT_DICTIONARY
                .getOrDefault(normalizedLabel, Map.of())
                .getOrDefault(languageCode, label.getLabel());

        return DetectedObjectDTO.builder()
                .label(label.getLabel())
                .confidence(label.getConfidence())
                .nativeWord(label.getLabel())
                .learningWord(learningWord)
                .languageCode(languageCode)
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class YoloLabel {
        private String label;
        private double confidence;
    }
}
