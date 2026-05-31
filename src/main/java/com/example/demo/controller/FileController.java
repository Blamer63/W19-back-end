package com.example.demo.controller;

import com.example.demo.service.S3Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/files")
public class FileController {

    // Images are uploaded inline during entity creation (post, message, profile avatar).
    // This endpoint only handles non-image media that doesn't attach to a specific entity.
    private static final Set<String> ALLOWED_TYPES = Set.of("audio", "videos");

    private static final long MAX_AUDIO_SIZE = 20L * 1024 * 1024;  //  20 MB
    private static final long MAX_VIDEO_SIZE = 100L * 1024 * 1024; // 100 MB

    private static final Set<String> AUDIO_MIME_TYPES = Set.of(
            "audio/mpeg", "audio/wav", "audio/ogg", "audio/mp4", "audio/aac", "audio/x-m4a"
    );
    private static final Set<String> VIDEO_MIME_TYPES = Set.of(
            "video/mp4", "video/mpeg", "video/quicktime", "video/x-msvideo", "video/webm"
    );

    private final S3Service s3Service;

    public FileController(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    /**
     * Upload an audio or video file to S3.
     *
     * Usage:
     *   POST /api/files/upload?type=audio   (with multipart form field "file")
     *   POST /api/files/upload?type=videos
     *
     * Returns: { "url": "https://<cloudfront-domain>/audio/uuid-name.mp3" }
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) throws IOException {

        if (!ALLOWED_TYPES.contains(type)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid type. Must be one of: audio, videos"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File must not be empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !getAllowedMimeTypes(type).contains(contentType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid file type for " + type + ". Allowed: " + getAllowedMimeTypes(type)));
        }

        long maxSize = getMaxSize(type);
        if (file.getSize() > maxSize) {
            long limitMb = maxSize / (1024 * 1024);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File exceeds the " + limitMb + " MB limit for " + type));
        }

        String url = s3Service.uploadFile(file, type);
        return ResponseEntity.ok(Map.of("url", url));
    }

    private Set<String> getAllowedMimeTypes(String type) {
        return switch (type) {
            case "audio"  -> AUDIO_MIME_TYPES;
            default       -> VIDEO_MIME_TYPES;
        };
    }

    private long getMaxSize(String type) {
        return switch (type) {
            case "audio"  -> MAX_AUDIO_SIZE;
            default       -> MAX_VIDEO_SIZE;
        };
    }

    /**
     * Delete a standalone audio or video file from S3 by its key.
     *
     * Usage:
     *   DELETE /api/files/delete?key=audio/uuid-name.mp3
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteFile(@RequestParam("key") String key) {
        if (!s3Service.isStandaloneMediaKey(key)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only standalone audio and video keys can be deleted here"));
        }

        s3Service.deleteFile(key);
        return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
    }
}
