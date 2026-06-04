package com.example.demo.service.scanner;

import com.example.demo.enums.ScanMode;
import com.example.demo.exception.ObjectDetectionUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class VisionServiceClient {

    private final RestTemplate restTemplate;
    private final String endpoint;

    @Autowired
    public VisionServiceClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.vision.endpoint}") String endpoint,
            @Value("${app.vision.timeout-ms:60000}") long timeoutMs) {
        Duration readTimeout = Duration.ofMillis(timeoutMs);
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(readTimeout)
                .build();
        this.endpoint = endpoint;
    }

    VisionServiceClient(RestTemplate restTemplate, String endpoint) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
    }

    public List<VisionDetection> analyze(String imageBase64, String languageCode) {
        return analyze(imageBase64, languageCode, ScanMode.PRECISION);
    }

    public List<VisionDetection> analyze(String imageBase64, String languageCode, ScanMode scanMode) {
        VisionAnalyzeResponse response = requestAnalysis(imageBase64, languageCode, scanMode);
        if (response.getError() != null && !response.getError().isBlank()) {
            log.warn("Vision service returned error: {}", response.getError());
        }
        return Optional.ofNullable(response.getDetections()).orElse(List.of());
    }

    public VisionAnalyzeResponse requestAnalysis(String imageBase64, String languageCode) {
        return requestAnalysis(imageBase64, languageCode, ScanMode.PRECISION);
    }

    public VisionAnalyzeResponse requestAnalysis(String imageBase64, String languageCode, ScanMode scanMode) {
        String language = (languageCode == null || languageCode.isBlank()) ? "en" : languageCode;
        ScanMode mode = scanMode == null ? ScanMode.PRECISION : scanMode;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> requestBody = Map.of(
                "image", imageBase64,
                "language", language,
                "mode", mode.getWireValue());

        try {
            ResponseEntity<VisionAnalyzeResponse> response = restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(requestBody, headers),
                    VisionAnalyzeResponse.class);

            VisionAnalyzeResponse body = response.getBody();
            if (body == null) {
                log.warn("Vision service returned empty response body from {}", endpoint);
                throw new ObjectDetectionUnavailableException("Object detection service unavailable");
            }

            return body;
        } catch (RestClientException ex) {
            log.warn("Vision service request failed for endpoint {}: {}", endpoint, ex.getMessage());
            throw new ObjectDetectionUnavailableException("Object detection service unavailable", ex);
        }
    }
}
