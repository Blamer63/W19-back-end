package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.PracticeSession;
import com.example.demo.service.PracticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/learn")
@RequiredArgsConstructor
public class PracticeController {

        private final PracticeService practiceService;

        @PostMapping("/sessions/start")
        public ResponseEntity<StartSessionResponse> startSession(
                        Authentication authentication,
                        @Valid @RequestBody StartSessionRequest request) {

                StartSessionResponse response = practiceService.startSession(
                                authentication.getName(),
                                request);

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @PostMapping("/sessions/{sessionId}/submit")
        public ResponseEntity<SubmitResultResponse> submitResult(
                        Authentication authentication,
                        @PathVariable UUID sessionId,
                        @Valid @RequestBody SubmitResultRequest request) {

                SubmitResultResponse response = practiceService.submitResult(
                                authentication.getName(),
                                sessionId,
                                request);

                return ResponseEntity.ok(response);
        }

        @PostMapping("/sessions/{sessionId}/complete")
        public ResponseEntity<CompleteSessionResponse> completeSession(
                        Authentication authentication,
                        @PathVariable UUID sessionId) {

                CompleteSessionResponse response = practiceService.completeSession(
                                authentication.getName(),
                                sessionId);

                return ResponseEntity.ok(response);
        }

        @GetMapping("/sessions")
        public ResponseEntity<Page<PracticeSession>> getPracticeHistory(
                        Authentication authentication,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {

                Page<PracticeSession> history = practiceService.getPracticeHistory(
                                authentication.getName(),
                                page,
                                size);

                return ResponseEntity.ok(history);
        }
}
