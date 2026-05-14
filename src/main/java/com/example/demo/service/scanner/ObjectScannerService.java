package com.example.demo.service.scanner;

import com.example.demo.dto.DetectedObjectResponse;
import com.example.demo.dto.ScannerAnalyzeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObjectScannerService {

    private final VisionServiceClient visionServiceClient;

    public DetectedObjectResponse analyze(ScannerAnalyzeRequest request) {

        String language = request.getTargetLanguage() != null
                ? request.getTargetLanguage()
                : "en";

        // 1. Call Vision Service — language is forwarded so FastAPI applies
        //    the local translation lookup. Labels in the response are already
        //    in the requested language.
        VisionServiceClient.VisionResponse visionResponse =
                visionServiceClient.analyze(request.getImageBase64(), language);

        log.info("Vision response for language='{}': labels={} | description={}",
                language, visionResponse.getLabels(), visionResponse.getDescription());

        List<String> labels = visionResponse.getLabels() != null
                ? visionResponse.getLabels()
                : Collections.emptyList();

        String description = visionResponse.getDescription() != null
                ? visionResponse.getDescription()
                : "";

        if (labels.isEmpty()) {
            log.warn("Vision service returned empty labels — check VisionServiceClient logs for raw response.");
        }

        // Vision-service already returns translated labels via the local JSON
        // translation store. We expose them in both fields so callers can use
        // either the canonical 'labels' or 'translated_labels' field.
        return DetectedObjectResponse.builder()
                .labels(labels)
                .description(description)
                .translatedLabels(labels)   // same — FastAPI already translated
                .build();
    }
}
