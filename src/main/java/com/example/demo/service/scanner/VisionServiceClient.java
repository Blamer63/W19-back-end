package com.example.demo.service.scanner;

import com.example.demo.dto.DetectedObjectDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     *   {
     *     "detections": [ {"canonical_label": "chair", "translated_label": "silla"} ],
     *     "description": "objects detected: silla",
     *     "language": "es",
     *     "error": "..."   (only on failure)
     *   }
     *
     * @JsonIgnoreProperties(ignoreUnknown = true) prevents Jackson failures
     * when FastAPI adds extra fields.
     * No @Builder — @Builder removes the public no-args constructor which
     * breaks Jackson deserialization.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VisionResponse {
        private List<DetectedObjectDTO> detections;
        private String description;
        private String language;
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
                return emptyResponse(lang);
            }
            if (raw.getBody() == null || raw.getBody().isBlank()) {
                log.error("[VisionClient] Empty/null body from vision service.");
                return emptyResponse(lang);
            }

            // ── Step 3: Explicit deserialization with exception logging ────────
            VisionResponse parsed;
            try {
                parsed = MAPPER.readValue(raw.getBody(), VisionResponse.class);
            } catch (Exception deserEx) {
                log.error("[VisionClient] Jackson deserialization FAILED. body='{}' error={}",
                        raw.getBody(), deserEx.getMessage(), deserEx);
                return emptyResponse(lang);
            }

            if (parsed.getDetections() == null || parsed.getDetections().isEmpty()) {
                log.warn("[VisionClient] Deserialized successfully but detections is empty/null. parsed={}",
                        parsed);
            }
            if (parsed.getError() != null && !parsed.getError().isEmpty()) {
                log.error("[VisionClient] FastAPI returned error field: {}", parsed.getError());
            }

            log.info("[VisionClient] ✓ detections={} | description={}",
                    parsed.getDetections(), parsed.getDescription());

            return parsed;

        } catch (Exception e) {
            log.error("[VisionClient] HTTP call to {} FAILED: {}", url, e.getMessage(), e);
            return emptyResponse(lang);
        }
    }

    /** Backwards-compatible overload — defaults language to "en". */
    public VisionResponse analyze(String imageBase64) {
        return analyze(imageBase64, "en");
    }

    private VisionResponse emptyResponse(String language) {
        VisionResponse r = new VisionResponse();
        r.setDetections(Collections.emptyList());
        r.setDescription("");
        r.setLanguage(language);
        return r;
    }
}

