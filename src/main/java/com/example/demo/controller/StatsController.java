package com.example.demo.controller;

import com.example.demo.dto.LearningStatsResponse;
import com.example.demo.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learn")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/stats")
    public ResponseEntity<LearningStatsResponse> getLearningStats(Authentication authentication) {
        LearningStatsResponse stats = statsService.getLearningStats(authentication.getName());
        return ResponseEntity.ok(stats);
    }
}
