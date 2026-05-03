package com.example.demo.service.scanner;

import com.example.demo.config.ScannerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class HttpTranslationProvider implements TranslationProvider {

    private final RestTemplate restTemplate;
    private final ScannerProperties scannerProperties;

    @Override
    @SuppressWarnings("unchecked")
    public String translate(String text, String targetLanguage) {
        if (scannerProperties.getTranslationUrl() == null || scannerProperties.getTranslationUrl().isBlank()) {
            throw new IllegalStateException("Translation API URL is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "text", text,
                "target_language", targetLanguage);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                scannerProperties.getTranslationUrl(),
                new HttpEntity<>(body, headers),
                Map.class);

        if (response.getBody() == null || response.getBody().get("translated_text") == null) {
            throw new IllegalStateException("Translation API returned invalid response");
        }
        return String.valueOf(response.getBody().get("translated_text"));
    }
}
