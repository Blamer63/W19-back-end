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

    // Allowed type values — maps directly to the S3 folder name
    private static final Set<String> ALLOWED_TYPES = Set.of("images", "audio", "videos");

    private final S3Service s3Service;

    public FileController(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    /**
     * Upload a file to S3.
     *
     * Usage:
     *   POST /api/files/upload?type=images   (with multipart form field "file")
     *   POST /api/files/upload?type=audio
     *   POST /api/files/upload?type=videos
     *
     * Returns: { "url": "https://bucket.s3.region.amazonaws.com/images/uuid-name.jpg" }
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) throws IOException {

        if (!ALLOWED_TYPES.contains(type)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid type. Must be one of: images, audio, videos"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File must not be empty"));
        }

        String url = s3Service.uploadFile(file, type);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * Delete a file from S3 by its key.
     *
     * Usage:
     *   DELETE /api/files/delete?key=images/uuid-name.jpg
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteFile(@RequestParam("key") String key) {
        s3Service.deleteFile(key);
        return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
    }
}
