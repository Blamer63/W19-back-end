package com.example.demo.controller;

import com.example.demo.dto.ScannerAnalyzeRequest;
import com.example.demo.dto.DetectedObjectResponse;
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
    public ResponseEntity<DetectedObjectResponse> analyze(@Valid @RequestBody ScannerAnalyzeRequest request) {
        log.info("Scanner analyze request received for target language {}", request.getTargetLanguage());
        log.info("Incoming image length: {}", request.getImageBase64() != null ? request.getImageBase64().length() : 0);
        return ResponseEntity.ok(objectScannerService.analyze(request));
    }

    /**
     * Integration smoke-test: calls FastAPI /debug-test (hardcoded response)
     * via the SAME VisionServiceClient and deserialization path as /analyze.
     *
     * GET /api/scanner/debug-test
     *
     * Expected result if transport + Jackson are healthy:
     *   { "labels": ["chair", "vase"], "description": "objects detected: chair, vase" }
     *
     * If this also returns labels=[] then the bug is in VisionResponse
     * Jackson deserialization (previously caused by @Builder removing the
     * public no-args constructor). Check Spring Boot logs for
     * [VisionClient] Jackson deserialization FAILED.
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

