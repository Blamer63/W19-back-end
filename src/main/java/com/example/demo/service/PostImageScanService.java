package com.example.demo.service;

import com.example.demo.dto.ScanPostImageRequest;
import com.example.demo.dto.ScanResponse;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostImage;
import com.example.demo.enums.PostStatus;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
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
        return scanPostImage(postId, currentUserEmail, null);
    }

    @Transactional
    public ScanResponse scanPostImage(UUID postId, String currentUserEmail, ScanPostImageRequest request) {
        profileRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!canAccessPost(post, currentUserEmail)) {
            throw new ResourceNotFoundException("Post not found");
        }

        String imageUrl = selectImageUrl(post, request);
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

    private String selectImageUrl(Post post, ScanPostImageRequest request) {
        List<String> imageUrls = orderedImageUrls(post);
        if (imageUrls.isEmpty() && post.getImageUrl() != null && !post.getImageUrl().isBlank()) {
            imageUrls = List.of(post.getImageUrl());
        }

        if (request == null || (isBlank(request.getImageUrl()) && request.getImageIndex() == null)) {
            return imageUrls.isEmpty() ? null : imageUrls.get(0);
        }

        String requestedUrl = normalizeUrl(request.getImageUrl());
        Integer requestedIndex = request.getImageIndex();

        if (requestedIndex != null) {
            if (requestedIndex < 0 || requestedIndex >= imageUrls.size()) {
                throw new IllegalArgumentException("Selected image does not belong to this post");
            }
            String indexedUrl = imageUrls.get(requestedIndex);
            if (!requestedUrl.isBlank() && !indexedUrl.equals(requestedUrl)) {
                throw new IllegalArgumentException("Selected image does not belong to this post");
            }
            return indexedUrl;
        }

        if (!requestedUrl.isBlank() && imageUrls.contains(requestedUrl)) {
            return requestedUrl;
        }

        throw new IllegalArgumentException("Selected image does not belong to this post");
    }

    private List<String> orderedImageUrls(Post post) {
        if (post.getImages() == null || post.getImages().isEmpty()) {
            return List.of();
        }
        return post.getImages().stream()
                .sorted(Comparator.comparingInt(PostImage::getPosition))
                .map(PostImage::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();
    }

    private String normalizeUrl(String url) {
        return url == null ? "" : url.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean canAccessPost(Post post, String currentUserEmail) {
        if (post.getAuthor() != null && currentUserEmail.equals(post.getAuthor().getEmail())) {
            return true;
        }
        return PUBLIC_SCAN_STATUSES.contains(post.getStatus());
    }
}
