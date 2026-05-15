package com.example.demo.service.scanner;

import com.example.demo.dto.ScannerAnalyzeRequest;
import com.example.demo.dto.ScannerAnalyzeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObjectScannerService {

    private final VisionServiceClient visionServiceClient;

    /**
     * Calls the vision-service /analyze endpoint and forwards the structured
     * detections response directly to the controller.
     *
     * Translation is handled entirely inside vision-service using the static
     * JSON taxonomy files (translation_store.py). Spring Boot performs NO
     * additional translation — it only forwards the already-localised output.
     */
    public ScannerAnalyzeResponse analyze(ScannerAnalyzeRequest request) {

        String language = (request.getTargetLanguage() != null && !request.getTargetLanguage().isBlank())
                ? request.getTargetLanguage()
                : "en";

        VisionServiceClient.VisionResponse visionResponse =
                visionServiceClient.analyze(request.getImageBase64(), language);

        log.info("Vision response for language='{}': detections={} | description={}",
                language, visionResponse.getDetections(), visionResponse.getDescription());

        if (visionResponse.getDetections() == null || visionResponse.getDetections().isEmpty()) {
            log.warn("Vision service returned empty detections — check VisionServiceClient logs for raw response.");
        }

        return ScannerAnalyzeResponse.builder()
                .detections(visionResponse.getDetections() != null
                        ? visionResponse.getDetections()
                        : Collections.emptyList())
                .description(visionResponse.getDescription() != null
                        ? visionResponse.getDescription()
                        : "")
                .language(language)
                .build();
    }
}

