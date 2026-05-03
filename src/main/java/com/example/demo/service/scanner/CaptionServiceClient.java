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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class CaptionServiceClient {

    private final RestTemplate restTemplate;
    private final String captionServiceUrl;

    public CaptionServiceClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${ml.caption-service.url:http://localhost:8004}") String captionServiceUrl) {
        
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(10)) // Captioning might take a bit longer
                .build();
        this.captionServiceUrl = captionServiceUrl;
    }

    @Data
    @Builder
    public static class CaptionResponse {
        private String description;
        private String label;
        private String error;
        private boolean isTimeout;
    }

    public CompletableFuture<CaptionResponse> captionAsync(String imageBase64) {
        return CompletableFuture.supplyAsync(() -> caption(imageBase64))
                .exceptionally(ex -> {
                    log.error("Error communicating with Caption Service: {}", ex.getMessage());
                    return CaptionResponse.builder()
                            .isTimeout(true)
                            .description("")
                            .label("unknown")
                            .build();
                });
    }

    private CaptionResponse caption(String imageBase64) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "image_base64", imageBase64
            );

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<CaptionResponse> response = restTemplate.postForEntity(
                    captionServiceUrl + "/caption",
                    requestEntity,
                    CaptionResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                if (response.getBody().getError() != null && !response.getBody().getError().isEmpty()) {
                    log.error("Caption Service error: {}", response.getBody().getError());
                }
                return response.getBody();
            } else {
                log.warn("Caption Service returned status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Caption API call failed", e);
        }
        
        return CaptionResponse.builder()
                .description("")
                .label("unknown")
                .build();
    }
}
