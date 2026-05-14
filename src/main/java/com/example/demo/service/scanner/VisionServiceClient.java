package com.example.demo.service.scanner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    // Shared, thread-safe ObjectMapper.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public VisionServiceClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${VISION_API_URL}") String visionServiceUrl) {

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
        this.visionServiceUrl = visionServiceUrl;
    }

    /**
     * DTO that maps the FastAPI /analyze response:
     *   { "labels": [...], "description": "...", "error": "..." }
     *
     * IMPORTANT:
     *   - @JsonIgnoreProperties(ignoreUnknown = true) prevents Jackson from
     *     failing when FastAPI adds extra fields.
     *   - No @Builder here — @Builder generates a private no-args constructor
     *     which breaks Jackson deserialization. Use plain @Data only.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VisionResponse {
        private List<String> labels;
        private String description;
        private String error;
    }

    /**
     * Call FastAPI /analyze.
     *
     * @param imageBase64 Base64-encoded image string (no data-URL prefix).
     * @param language    BCP-47 language code (en / es / fr / ja).
     *                    Sent as "language" — matches FastAPI AnalyzeRequest exactly.
     */
    public VisionResponse analyze(String imageBase64, String language) {
        String lang = (language != null && !language.isBlank()) ? language : "en";
        String url  = visionServiceUrl + "/analyze";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Field name "language" matches FastAPI AnalyzeRequest schema exactly.
            Map<String, Object> requestBody = Map.of(
                    "image",    imageBase64,
                    "language", lang
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("[VisionClient] → POST {} | language={} | image_len={}",
                    url, lang, imageBase64.length());

            // ── Step 1: Fetch raw String body ─────────────────────────────────
            // Must be String.class so we can log before Jackson touches it.
            ResponseEntity<String> raw = restTemplate.postForEntity(url, entity, String.class);

            // ── Step 2: Full diagnostic log ───────────────────────────────────
            log.info("[VisionClient] ← HTTP {}", raw.getStatusCode());
            log.info("[VisionClient] ← Content-Type: {}",
                    raw.getHeaders().getContentType());
            log.info("[VisionClient] ← Raw body: {}", raw.getBody());

            if (!raw.getStatusCode().is2xxSuccessful()) {
                log.error("[VisionClient] Non-2xx status {}. Body: {}", raw.getStatusCode(), raw.getBody());
                return emptyResponse();
            }
            if (raw.getBody() == null || raw.getBody().isBlank()) {
                log.error("[VisionClient] Empty/null body from vision service.");
                return emptyResponse();
            }

            // ── Step 3: Explicit deserialization with exception logging ────────
            VisionResponse parsed;
            try {
                parsed = MAPPER.readValue(raw.getBody(), VisionResponse.class);
            } catch (Exception deserEx) {
                log.error("[VisionClient] Jackson deserialization FAILED. body='{}' error={}",
                        raw.getBody(), deserEx.getMessage(), deserEx);
                return emptyResponse();
            }

            if (parsed.getLabels() == null || parsed.getLabels().isEmpty()) {
                log.warn("[VisionClient] Deserialized successfully but labels is empty/null. parsed={}",
                        parsed);
            }
            if (parsed.getError() != null && !parsed.getError().isEmpty()) {
                log.error("[VisionClient] FastAPI returned error field: {}", parsed.getError());
            }

            log.info("[VisionClient] ✓ parsed labels={} | description={}",
                    parsed.getLabels(), parsed.getDescription());

            return parsed;

        } catch (Exception e) {
            log.error("[VisionClient] HTTP call to {} FAILED: {}", url, e.getMessage(), e);
            return emptyResponse();
        }
    }

    /** Backwards-compatible overload — defaults language to "en". */
    public VisionResponse analyze(String imageBase64) {
        return analyze(imageBase64, "en");
    }

    private VisionResponse emptyResponse() {
        VisionResponse r = new VisionResponse();
        r.setLabels(Collections.emptyList());
        r.setDescription("");
        return r;
    }
}
