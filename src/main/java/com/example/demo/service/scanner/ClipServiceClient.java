package com.example.demo.service.scanner;

import com.example.demo.config.ScannerProperties;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class ClipServiceClient {

    private final RestTemplate restTemplate;
    private final ScannerProperties scannerProperties;
    private final ExecutorService executorService;

    public ClipServiceClient(RestTemplateBuilder restTemplateBuilder, ScannerProperties scannerProperties) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(2000))
                .setReadTimeout(Duration.ofMillis(8000))
                .build();
        this.scannerProperties = scannerProperties;
        // Bounded concurrency: max 3 concurrent requests
        this.executorService = Executors.newFixedThreadPool(3);
    }

    @Data
    @Builder
    public static class ClipPrediction {
        private String label;
        private Double confidence;
        private boolean isTimeout;
    }

    public CompletableFuture<List<ClipPrediction>> classifyAsync(String cropBase64, String yoloLabel) {
        return CompletableFuture.supplyAsync(() -> classify(cropBase64, yoloLabel), executorService)
                .exceptionally(ex -> {
                    log.warn("CLIP classification failed asynchronously: {}", ex.getMessage());
                    if (ex.getCause() instanceof java.net.SocketTimeoutException || ex instanceof java.net.SocketTimeoutException) {
                        return List.of(ClipPrediction.builder().isTimeout(true).build());
                    }
                    return Collections.emptyList();
                });
    }

    @SuppressWarnings("unchecked")
    private List<ClipPrediction> classify(String cropBase64, String yoloLabel) {
        if (scannerProperties.getClipUrl() == null || scannerProperties.getClipUrl().isBlank()) {
            return Collections.emptyList();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "image_base64", cropBase64,
                "yolo_label", yoloLabel != null ? yoloLabel : "",
                "top_k", 5);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    scannerProperties.getClipUrl(),
                    new HttpEntity<>(body, headers),
                    Map.class);

            if (response.getBody() == null || response.getBody().containsKey("error")) {
                log.warn("CLIP service returned error or null");
                return Collections.emptyList();
            }

            List<Map<String, Object>> predictions = (List<Map<String, Object>>) response.getBody().get("predictions");
            if (predictions == null) {
                return Collections.emptyList();
            }

            return predictions.stream()
                    .filter(Objects::nonNull)
                    .map(p -> ClipPrediction.builder()
                            .label(String.valueOf(p.getOrDefault("label", "")))
                            .confidence(parseConfidence(p.get("confidence")))
                            .build())
                    .filter(p -> !p.getLabel().isBlank())
                    .toList();

        } catch (Exception e) {
            log.warn("Failed to call CLIP service: {}", e.getMessage());
            if (e.getCause() instanceof java.net.SocketTimeoutException || e instanceof java.net.SocketTimeoutException || e.getMessage().contains("timed out")) {
                return List.of(ClipPrediction.builder().isTimeout(true).build());
            }
            return Collections.emptyList();
        }
    }

    private double parseConfidence(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0.0d;
        }
    }
}
