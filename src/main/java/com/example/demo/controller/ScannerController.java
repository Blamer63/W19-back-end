package com.example.demo.controller;

import com.example.demo.dto.ScannerAnalyzeRequest;
import com.example.demo.dto.ScannerAnalyzeResponse;
import com.example.demo.service.scanner.ObjectScannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
@Slf4j
public class ScannerController {

    private final ObjectScannerService objectScannerService;

    @PostMapping("/analyze")
    public ResponseEntity<ScannerAnalyzeResponse> analyze(@Valid @RequestBody ScannerAnalyzeRequest request) {
        log.info("Scanner analyze request received for target language {}", request.getTargetLanguage());
        return ResponseEntity.ok(objectScannerService.analyze(request));
    }
}
