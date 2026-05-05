package com.example.demo.controller;

import com.example.demo.dto.PostResponse;
import com.example.demo.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final com.example.demo.service.TranslationService translationService;
    private final com.example.demo.service.ReportService reportService;
    private final com.example.demo.repository.ProfileRepository profileRepository;

    @GetMapping
    public ResponseEntity<Page<PostResponse>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            Authentication authentication) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity
                .ok(postService.getFeed(language, pageable, latitude, longitude, authentication.getName()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @RequestParam("content") String content,
            @RequestParam(value = "original_language", required = false) String originalLanguage,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestPart(value = "image", required = false) MultipartFile image,
            Authentication authentication) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.createPost(content, originalLanguage, latitude, longitude, image,
                        authentication.getName()));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @PathVariable UUID postId,
            Authentication authentication) {
        return ResponseEntity.ok(postService.getPost(postId, authentication.getName()));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID postId,
            Authentication authentication) {
        postService.deletePost(postId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{postId}/translations")
    public ResponseEntity<com.example.demo.dto.PostTranslationResponse> translatePost(
            @PathVariable UUID postId,
            @RequestParam(name = "target_language") String targetLanguage) {
        return ResponseEntity.ok(translationService.getTranslation(postId, targetLanguage));
    }

    @PostMapping("/{postId}/reports")
    public ResponseEntity<Void> reportPost(
            Authentication authentication,
            @PathVariable UUID postId,
            @RequestBody com.example.demo.dto.ReportRequest request) {
        com.example.demo.entity.Profile reporter = profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        request.setPostId(postId);
        reportService.createReport(reporter.getId(), request);
        return ResponseEntity.ok().build();
    }
}
