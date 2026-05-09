package com.example.demo.service.scanner;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class VisionServiceClient {

    private final RestTemplate restTemplate;
    private final String visionServiceUrl;

    public VisionServiceClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${VISION_API_URL}") String visionServiceUrl) {

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
        this.visionServiceUrl = visionServiceUrl;
    }

    @Data
    @Builder
    public static class VisionResponse {
        private List<String> labels;
        private String description;
        private String error;
    }

    public VisionResponse analyze(String imageBase64) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of("image", imageBase64);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            log.info("Sending to vision service at {}/analyze, image length: {}", visionServiceUrl,
                    imageBase64.length());
            ResponseEntity<VisionResponse> response = restTemplate.postForEntity(
                    visionServiceUrl + "/analyze",
                    requestEntity,
                    VisionResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Vision response received: {}", response.getBody());
                if (response.getBody().getError() != null && !response.getBody().getError().isEmpty()) {
                    log.error("Vision Service error: {}", response.getBody().getError());
                }
                return response.getBody();
            } else {
                log.warn("Vision Service returned status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Vision API call failed: {}", e.getMessage(), e);
        }

        return VisionResponse.builder()
                .labels(Collections.emptyList())
                .description("")
                .build();
    }
}
