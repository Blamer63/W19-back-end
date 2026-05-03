package com.example.demo.service.scanner;

import com.example.demo.config.ScannerProperties;
import com.example.demo.dto.DetectedObjectResponse;
import com.example.demo.dto.ScannerAnalyzeRequest;
import com.example.demo.dto.ScannerAnalyzeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObjectScannerService {

    private final ObjectDetectionProvider objectDetectionProvider;
    private final CaptionServiceClient captionServiceClient;
    private final TranslationProvider translationProvider;
    private final InMemoryTranslationCache translationCache;
    private final ScannerProperties scannerProperties;

    public ScannerAnalyzeResponse analyze(ScannerAnalyzeRequest request) {
        double confidenceThreshold = request.getConfidenceThreshold() != null
                ? request.getConfidenceThreshold()
                : scannerProperties.getDefaultConfidenceThreshold();

        int maxResults = request.getMaxResults() != null
                ? request.getMaxResults()
                : scannerProperties.getDefaultMaxResults();

        // Enforce an absolute maximum of 5 to protect performance
        maxResults = Math.min(maxResults, 5);

        // 1. YOLO Detection
        List<DetectedObject> detectedObjects = objectDetectionProvider.detect(
                request.getImageBase64(),
                confidenceThreshold,
                maxResults);

        if (detectedObjects.isEmpty()) {
            return ScannerAnalyzeResponse.builder()
                    .status("NO_OBJECTS")
                    .message("No objects detected")
                    .targetLanguage(request.getTargetLanguage())
                    .detectionCount(0)
                    .detections(Collections.emptyList())
                    .build();
        }

        // 2. Generate Caption and Extract Vocabulary Asynchronously
        List<CompletableFuture<DetectedObjectResponse>> futureDetections = detectedObjects.stream()
                .map(yoloObj -> captionServiceClient.captionAsync(yoloObj.getCropBase64())
                        .thenApply(captionResponse -> {
                            String yoloLabel = yoloObj.getYoloLabel();
                            double yoloConf = yoloObj.getYoloConfidence();
                            
                            String finalLabel = yoloLabel;
                            String finalDescription = "";

                            if (captionResponse != null && !captionResponse.isTimeout()) {
                                if (captionResponse.getLabel() != null && !captionResponse.getLabel().isEmpty() && !captionResponse.getLabel().equals("unknown")) {
                                    finalLabel = captionResponse.getLabel();
                                }
                                if (captionResponse.getDescription() != null) {
                                    finalDescription = captionResponse.getDescription();
                                }
                                log.info("[PIPELINE] Captioning Success | YOLO: {} -> Label: {}, Desc: {}", yoloLabel, finalLabel, finalDescription);
                            } else {
                                log.warn("[PIPELINE] Captioning Failed or Timeout | Falling back to YOLO label: {}", yoloLabel);
                            }

                            // 3. Translate both label and description
                            String translatedLabel = translateSafely(finalLabel, request.getTargetLanguage());
                            String translatedDescription = finalDescription;
                            if (!finalDescription.isEmpty()) {
                                translatedDescription = translateSafely(finalDescription, request.getTargetLanguage());
                            }

                            boolean translated = !translatedLabel.equalsIgnoreCase(finalLabel);

                            return DetectedObjectResponse.builder()
                                    .label(finalLabel)
                                    .translatedLabel(translatedLabel)
                                    .description(finalDescription)
                                    .translatedDescription(translatedDescription)
                                    .confidence(yoloConf) // Maintain YOLO confidence for sorting purposes
                                    .translated(translated)
                                    .build();
                        }))
                .toList();

        // Wait for all requests to complete
        List<DetectedObjectResponse> finalDetections = futureDetections.stream()
                .map(CompletableFuture::join)
                .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
                .toList();

        return ScannerAnalyzeResponse.builder()
                .status("OK")
                .message("Objects detected and described")
                .targetLanguage(request.getTargetLanguage())
                .detectionCount(finalDetections.size())
                .detections(finalDetections)
                .build();
    }

    private String translateSafely(String text, String targetLanguage) {
        if (text == null || text.trim().isEmpty()) {
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
