package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level response returned by POST /api/scanner/analyze.
 *
 * Mirrors the vision-service FastAPI response exactly:
 *   {
 *     "detections": [ { "canonical_label": "chair", "translated_label": "silla" } ],
 *     "description": "objects detected: silla",
 *     "language": "es"
 *   }
 *
 * Spring Boot does NOT perform any translation — it forwards the
 * structured detections produced by translation_store.py inside vision-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScannerAnalyzeResponse {

    private List<DetectedObjectDTO> detections;

    private String description;

    /** Echoes the requested target language (en / es / fr / ja). */
    private String language;
}
