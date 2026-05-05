package com.example.demo.controller;

import com.example.demo.dto.ScanResponse;
import com.example.demo.service.ObjectDetectionService;
import com.example.demo.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class ScanController {

    private final ObjectDetectionService objectDetectionService;
    private final S3Service s3Service;

    @PostMapping(value = "/api/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ScanResponse> scan(
            @RequestPart("image") MultipartFile image,
            Authentication authentication) throws IOException {
        s3Service.validateImageFile(image);

        return ResponseEntity.ok(ScanResponse.builder()
                .detectedObjects(objectDetectionService.detect(image, authentication.getName()))
                .build());
    }
}
