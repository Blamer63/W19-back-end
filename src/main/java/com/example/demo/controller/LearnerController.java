package com.example.demo.controller;

import com.example.demo.dto.LearnerResponse;
import com.example.demo.service.LearnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learners")
@RequiredArgsConstructor
public class LearnerController {

    private final LearnerService learnerService;

    @GetMapping("/nearby")
    public ResponseEntity<Map<String, Object>> getNearbyLearners(
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "radius_km", defaultValue = "10") Double radiusKm,
            @RequestParam(value = "language", required = false) String languageCode,
            Authentication authentication) {

        if (latitude != null && (latitude < -90 || latitude > 90)) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }

        if (longitude != null && (longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }

        if (radiusKm <= 0) {
            throw new IllegalArgumentException("Radius must be greater than 0");
        }

        List<LearnerResponse> learners = learnerService.findNearbyLearners(
                latitude,
                longitude,
                radiusKm,
                languageCode,
                authentication.getName());

        Map<String, Object> response = new HashMap<>();
        response.put("learners", learners);

        return ResponseEntity.ok(response);
    }
}
