package com.example.demo.service.scanner;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ClassifierServiceClient {

    private final RestTemplate restTemplate;
    private final String classifierServiceUrl;

    public ClassifierServiceClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${ml.classifier-service.url:http://localhost:8003}") String classifierServiceUrl) {
        
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.classifierServiceUrl = classifierServiceUrl;
    }

    @Data
    @Builder
    public static class ClassifierPrediction {
        private String label;
        private double confidence;
        private boolean isTimeout;
    }

    @Data
    public static class ClassifierResponse {
        private List<ClassifierPrediction> predictions;
        private double margin;
        private String error;
    }

    public CompletableFuture<ClassifierResponse> classifyAsync(String imageBase64) {
        return CompletableFuture.supplyAsync(() -> classify(imageBase64))
                .exceptionally(ex -> {
                    log.error("Error communicating with Classifier Service: {}", ex.getMessage());
                    ClassifierResponse response = new ClassifierResponse();
                    response.setPredictions(Collections.singletonList(
                            ClassifierPrediction.builder().isTimeout(true).build()
                    ));
                    return response;
                });
    }

    private ClassifierResponse classify(String imageBase64) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "image_base64", imageBase64
            );

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<ClassifierResponse> response = restTemplate.postForEntity(
                    classifierServiceUrl + "/classify",
                    requestEntity,
                    ClassifierResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                if (response.getBody().getError() != null) {
                    log.error("Classifier Service error: {}", response.getBody().getError());
                }
                return response.getBody();
            } else {
                log.warn("Classifier Service returned status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Classifier API call failed", e);
        }
        
        ClassifierResponse emptyResponse = new ClassifierResponse();
        emptyResponse.setPredictions(Collections.emptyList());
        return emptyResponse;
    }
}
