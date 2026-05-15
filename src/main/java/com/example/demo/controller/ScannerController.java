package com.example.demo.controller;

import com.example.demo.dto.ScannerAnalyzeRequest;
import com.example.demo.dto.ScannerAnalyzeResponse;
import com.example.demo.service.scanner.ObjectScannerService;
import com.example.demo.service.scanner.VisionServiceClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
@Slf4j
public class ScannerController {

    private final ObjectScannerService objectScannerService;
    private final VisionServiceClient visionServiceClient;

    @Value("${VISION_API_URL}")
    private String visionApiUrl;

    @PostMapping("/analyze")
    public ResponseEntity<ScannerAnalyzeResponse> analyze(@Valid @RequestBody ScannerAnalyzeRequest request) {
        log.info("Scanner analyze request received for target language {}", request.getTargetLanguage());
        log.info("Incoming image length: {}", request.getImageBase64() != null ? request.getImageBase64().length() : 0);
        return ResponseEntity.ok(objectScannerService.analyze(request));
    }

    /**
     * Integration smoke-test: calls FastAPI /debug-test (hardcoded response).
     * GET /api/scanner/debug-test
     *
     * Expected result:
     *   {
     *     "detections": [
     *       {"canonical_label": "chair", "translated_label": "chair"},
     *       {"canonical_label": "vase",  "translated_label": "vase"}
     *     ],
     *     "description": "objects detected: chair, vase",
     *     "language": "en"
     *   }
     */
    @GetMapping("/debug-test")
    public ResponseEntity<Map<String, Object>> debugTest() {
        log.info("[DebugTest] Calling FastAPI /debug-test via VisionServiceClient");

        // Reuse the same RestTemplate+ObjectMapper path as real analyze() calls.
        // Call the debug GET endpoint directly.
        try {
            RestTemplate rt = new RestTemplate();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = rt.getForObject(visionApiUrl + "/debug-test", Map.class);
            log.info("[DebugTest] FastAPI /debug-test raw response: {}", response);
            return ResponseEntity.ok(response != null ? response : Map.of("error", "null response"));
        } catch (Exception e) {
            log.error("[DebugTest] Failed to call FastAPI /debug-test: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

