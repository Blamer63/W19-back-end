package com.example.demo.service.scanner;

import com.example.demo.config.ScannerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class HttpObjectDetectionProvider implements ObjectDetectionProvider {

    private final RestTemplate restTemplate;
    private final ScannerProperties scannerProperties;

    public HttpObjectDetectionProvider(RestTemplateBuilder restTemplateBuilder, ScannerProperties scannerProperties) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.scannerProperties = scannerProperties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DetectedObject> detect(String imageBase64, double confidenceThreshold, int maxResults) {
        if (scannerProperties.getYoloUrl() == null || scannerProperties.getYoloUrl().isBlank()) {
            return Collections.emptyList();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "image_base64", imageBase64,
                "confidence_threshold", confidenceThreshold,
                "max_results", maxResults);

        int maxRetries = 1;
        int attempt = 0;
        ResponseEntity<Map> response = null;

        while (attempt <= maxRetries) {
            try {
                response = restTemplate.postForEntity(
                        scannerProperties.getYoloUrl(),
                        new HttpEntity<>(body, headers),
                        Map.class);
                break;
            } catch (RestClientException e) {
                attempt++;
                log.warn("Attempt {} failed when calling YOLO service: {}", attempt, e.getMessage());
                if (attempt > maxRetries) {
                    log.error("All attempts to call YOLO service failed.");
                    return Collections.emptyList();
                }
                try {
                    Thread.sleep(500); // short delay before retry
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (response == null || response.getBody() == null) {
            return Collections.emptyList();
        }

        if (response.getBody().containsKey("error")) {
            log.error("YOLO service returned error: {}", response.getBody().get("error"));
            return Collections.emptyList();
        }

        if (response.getBody().get("detections") == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> detections = (List<Map<String, Object>>) response.getBody().get("detections");
        return detections.stream()
                .filter(Objects::nonNull)
                .map(item -> DetectedObject.builder()
                        .yoloLabel(String.valueOf(item.getOrDefault("yolo_label", "")))
                        .yoloConfidence(parseConfidence(item.get("yolo_confidence")))
                        .cropBase64(String.valueOf(item.get("crop_base64")))
                        .build())
                .filter(d -> !d.getYoloLabel().isBlank() && d.getYoloConfidence() >= confidenceThreshold)
                .toList();
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
