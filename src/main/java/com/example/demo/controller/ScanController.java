package com.example.demo.controller;

import com.example.demo.dto.ScanResponse;
import com.example.demo.dto.ScanSessionSummaryResponse;
import com.example.demo.dto.SavedWordResponse;
import com.example.demo.service.ObjectDetectionService;
import com.example.demo.service.ScanSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ScanController {

    private final ObjectDetectionService objectDetectionService;
    private final ScanSessionService scanSessionService;

    @PostMapping(value = "/api/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ScanResponse> scan(
            @RequestPart("image") MultipartFile image,
            Authentication authentication) throws IOException {
        return ResponseEntity.ok(scanSessionService.recordScan(
                authentication.getName(),
                objectDetectionService.detect(image, authentication.getName())));
    }

    @GetMapping("/api/scan/sessions")
    public ResponseEntity<Page<ScanSessionSummaryResponse>> getScanSessions(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(scanSessionService.getScanSessions(authentication.getName(), page, size));
    }

    @GetMapping("/api/scan/sessions/{scanSessionId}")
    public ResponseEntity<ScanResponse> getScanSession(
            Authentication authentication,
            @PathVariable UUID scanSessionId) {
        return ResponseEntity.ok(scanSessionService.getScanSession(authentication.getName(), scanSessionId));
    }

    @PostMapping("/api/scan/detections/{detectionId}/save")
    public ResponseEntity<SavedWordResponse> saveDetection(
            Authentication authentication,
            @PathVariable UUID detectionId) {
        return ResponseEntity.ok(scanSessionService.saveDetection(authentication.getName(), detectionId));
    }
}
