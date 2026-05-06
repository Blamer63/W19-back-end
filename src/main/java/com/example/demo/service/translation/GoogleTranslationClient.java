package com.example.demo.service.translation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GoogleTranslationClient implements TranslationClient {

    private final TranslationProperties properties;
    private final RestTemplate restTemplate;

    public GoogleTranslationClient(TranslationProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        Duration timeout = Duration.ofMillis(properties.getTimeoutMs());
        this.restTemplate = restTemplateBuilder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public TranslationResult translate(String text, String sourceLanguage, String targetLanguage) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new TranslationProviderException("Translation API key is not configured");
        }

        String url = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                .queryParam("key", properties.getApiKey())
                .toUriString();

        Map<String, Object> body = new HashMap<>();
        body.put("q", text);
        body.put("target", targetLanguage);
        body.put("format", "text");
        if (StringUtils.hasText(sourceLanguage)) {
            body.put("source", sourceLanguage);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, headers),
                    Map.class);

            Object dataValue = response.getBody() != null ? response.getBody().get("data") : null;
            if (!(dataValue instanceof Map<?, ?> data)) {
                throw new TranslationProviderException("Translation provider returned an invalid response");
            }

            Object translationsValue = data.get("translations");
            if (!(translationsValue instanceof List<?> translations) || translations.isEmpty()
                    || !(translations.get(0) instanceof Map<?, ?> firstTranslation)) {
                throw new TranslationProviderException("Translation provider returned no translation");
            }

            Object translatedTextValue = firstTranslation.get("translatedText");
            if (!(translatedTextValue instanceof String translatedText) || !StringUtils.hasText(translatedText)) {
                throw new TranslationProviderException("Translation provider returned empty translated text");
            }

            Object detectedSourceLanguageValue = firstTranslation.get("detectedSourceLanguage");

            return TranslationResult.builder()
                    .translatedText(HtmlUtils.htmlUnescape(translatedText))
                    .detectedSourceLanguage(detectedSourceLanguageValue instanceof String detected
                            ? detected
                            : null)
                    .build();
        } catch (HttpClientErrorException e) {
            log.warn("Google Translation API rejected request with status {}", e.getStatusCode());
            throw new TranslationProviderException("Translation provider rejected the request", e);
        } catch (RestClientException e) {
            log.warn("Google Translation API request failed", e);
            throw new TranslationProviderException("Translation provider is unavailable", e);
        }
    }
}
