package com.example.demo.service;

import com.example.demo.dto.ScanResponse;
import com.example.demo.entity.Post;
import com.example.demo.enums.PostStatus;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostImageScanService {

    private static final Set<PostStatus> PUBLIC_SCAN_STATUSES = Set.of(PostStatus.ACTIVE, PostStatus.APPROVED);

    private final PostRepository postRepository;
    private final ProfileRepository profileRepository;
    private final S3Service s3Service;
    private final ObjectDetectionService objectDetectionService;
    private final ScanSessionService scanSessionService;

    @Transactional
    public ScanResponse scanPostImage(UUID postId, String currentUserEmail) {
        profileRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!canAccessPost(post, currentUserEmail)) {
            throw new ResourceNotFoundException("Post not found");
        }

        String imageUrl = post.getImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("Post has no image to scan");
        }

        String key = s3Service.extractKey(imageUrl);
        if (key == null) {
            throw new IllegalArgumentException("Post image is not stored in the configured object store");
        }

        S3Service.StoredObject image = s3Service.downloadFile(key);
        return scanSessionService.recordScan(
                currentUserEmail,
                objectDetectionService.detect(image.bytes(), image.contentType(), currentUserEmail));
    }

    private boolean canAccessPost(Post post, String currentUserEmail) {
        if (post.getAuthor() != null && currentUserEmail.equals(post.getAuthor().getEmail())) {
            return true;
        }
        return PUBLIC_SCAN_STATUSES.contains(post.getStatus());
    }
}
