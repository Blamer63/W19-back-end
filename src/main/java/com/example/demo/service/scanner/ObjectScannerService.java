package com.example.demo.service.scanner;

import com.example.demo.dto.DetectedObjectResponse;
import com.example.demo.dto.ScannerAnalyzeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObjectScannerService {

    private final VisionServiceClient visionServiceClient;
    private final TranslationProvider translationProvider;
    private final InMemoryTranslationCache translationCache;

    public DetectedObjectResponse analyze(ScannerAnalyzeRequest request) {
        
        // 1. Call Vision Service
        VisionServiceClient.VisionResponse visionResponse = visionServiceClient.analyze(request.getImageBase64());
        log.info("Vision response: {}", visionResponse);

        List<String> labels = visionResponse.getLabels() != null ? visionResponse.getLabels() : Collections.emptyList();
        String description = visionResponse.getDescription() != null ? visionResponse.getDescription() : "";

        // 2. Translate labels
        List<String> translatedLabels = new ArrayList<>();
        if (request.getTargetLanguage() != null && !request.getTargetLanguage().isEmpty()) {
            for (String label : labels) {
                translatedLabels.add(translateSafely(label, request.getTargetLanguage()));
            }
        }

        return DetectedObjectResponse.builder()
                .labels(labels)
                .description(description)
                .translatedLabels(translatedLabels)
                .build();
    }

    private String translateSafely(String text, String targetLanguage) {
        if (text == null || text.trim().isEmpty() || targetLanguage == null || targetLanguage.trim().isEmpty()) {
            return text;
        }
        return translationCache.get(text, targetLanguage)
                .orElseGet(() -> {
                    try {
                        String translated = translationProvider.translate(text, targetLanguage);
                        translationCache.put(text, targetLanguage, translated);
                        return translated;
                    } catch (Exception e) {
                        log.warn("Translation failed for text '{}' to '{}': {}", text, targetLanguage, e.getMessage());
                        return text;
                    }
                });
    }
}
