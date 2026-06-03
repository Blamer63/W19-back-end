package com.example.demo.service;

import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.dto.ScanPostImageRequest;
import com.example.demo.dto.ScanResponse;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostImage;
import com.example.demo.entity.Profile;
import com.example.demo.enums.PostStatus;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostImageScanServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private S3Service s3Service;
    @Mock private ObjectDetectionService objectDetectionService;
    @Mock private ScanSessionService scanSessionService;

    private PostImageScanService postImageScanService;

    @BeforeEach
    void setUp() {
        postImageScanService = new PostImageScanService(
                postRepository,
                profileRepository,
                s3Service,
                objectDetectionService,
                scanSessionService);
    }

    @Test
    void scanPostImageLoadsS3ObjectAndRecordsScan() {
        UUID postId = UUID.randomUUID();
        UUID scanSessionId = UUID.randomUUID();
        Post post = post("author@example.com", PostStatus.ACTIVE, "https://cdn.example.test/images/post.jpg");
        List<DetectedObjectDTO> detections = List.of(DetectedObjectDTO.builder().label("apple").build());
        ScanResponse response = ScanResponse.builder()
                .scanSessionId(scanSessionId)
                .detectedObjects(detections)
                .build();

        when(profileRepository.findByEmail("viewer@example.com")).thenReturn(Optional.of(profile("viewer@example.com")));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(s3Service.extractKey("https://cdn.example.test/images/post.jpg")).thenReturn("images/post.jpg");
        when(s3Service.downloadFile("images/post.jpg"))
                .thenReturn(new S3Service.StoredObject("image-bytes".getBytes(), "image/jpeg"));
        when(objectDetectionService.detect(any(byte[].class), any(), any()))
                .thenReturn(detections);
        when(scanSessionService.recordScan("viewer@example.com", detections)).thenReturn(response);

        ScanResponse result = postImageScanService.scanPostImage(postId, "viewer@example.com");

        assertThat(result).isSameAs(response);
        verify(objectDetectionService).detect(any(byte[].class), any(), any());
        verify(scanSessionService).recordScan("viewer@example.com", detections);
    }

    @Test
    void scanPostImageAllowsAuthorToScanHiddenOwnPost() {
        UUID postId = UUID.randomUUID();
        Post post = post("author@example.com", PostStatus.HIDDEN, "https://cdn.example.test/images/post.jpg");

        when(profileRepository.findByEmail("author@example.com")).thenReturn(Optional.of(profile("author@example.com")));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(s3Service.extractKey("https://cdn.example.test/images/post.jpg")).thenReturn("images/post.jpg");
        when(s3Service.downloadFile("images/post.jpg"))
                .thenReturn(new S3Service.StoredObject("image-bytes".getBytes(), "image/jpeg"));
        when(objectDetectionService.detect(any(byte[].class), any(), any()))
                .thenReturn(List.of());
        when(scanSessionService.recordScan("author@example.com", List.of()))
                .thenReturn(ScanResponse.builder().detectedObjects(List.of()).build());

        postImageScanService.scanPostImage(postId, "author@example.com");

        verify(s3Service).downloadFile("images/post.jpg");
    }

    @Test
    void scanPostImageScansSelectedCarouselImage() {
        UUID postId = UUID.randomUUID();
        Post post = post("author@example.com", PostStatus.ACTIVE, "https://cdn.example.test/images/first.jpg");
        post.getImages().add(postImage(post, "https://cdn.example.test/images/first.jpg", 0));
        post.getImages().add(postImage(post, "https://cdn.example.test/images/second.jpg", 1));

        when(profileRepository.findByEmail("viewer@example.com")).thenReturn(Optional.of(profile("viewer@example.com")));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(s3Service.extractKey("https://cdn.example.test/images/second.jpg")).thenReturn("images/second.jpg");
        when(s3Service.downloadFile("images/second.jpg"))
                .thenReturn(new S3Service.StoredObject("image-bytes".getBytes(), "image/jpeg"));
        when(objectDetectionService.detect(any(byte[].class), any(), any()))
                .thenReturn(List.of());
        when(scanSessionService.recordScan("viewer@example.com", List.of()))
                .thenReturn(ScanResponse.builder().detectedObjects(List.of()).build());

        postImageScanService.scanPostImage(
                postId,
                "viewer@example.com",
                ScanPostImageRequest.builder()
                        .imageUrl("https://cdn.example.test/images/second.jpg")
                        .imageIndex(1)
                        .build());

        verify(s3Service).downloadFile("images/second.jpg");
    }

    @Test
    void scanPostImageRejectsSelectedImageMismatch() {
        UUID postId = UUID.randomUUID();
        Post post = post("author@example.com", PostStatus.ACTIVE, "https://cdn.example.test/images/first.jpg");
        post.getImages().add(postImage(post, "https://cdn.example.test/images/first.jpg", 0));
        post.getImages().add(postImage(post, "https://cdn.example.test/images/second.jpg", 1));

        when(profileRepository.findByEmail("viewer@example.com")).thenReturn(Optional.of(profile("viewer@example.com")));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postImageScanService.scanPostImage(
                postId,
                "viewer@example.com",
                ScanPostImageRequest.builder()
                        .imageUrl("https://other.example.test/images/hijack.jpg")
                        .imageIndex(1)
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selected image does not belong to this post");

        verifyNoInteractions(s3Service);
        verifyNoInteractions(objectDetectionService);
        verifyNoInteractions(scanSessionService);
    }

    @Test
    void scanPostImageRejectsHiddenPostForNonAuthor() {
        UUID postId = UUID.randomUUID();
        Post post = post("author@example.com", PostStatus.HIDDEN, "https://cdn.example.test/images/post.jpg");

        when(profileRepository.findByEmail("viewer@example.com")).thenReturn(Optional.of(profile("viewer@example.com")));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postImageScanService.scanPostImage(postId, "viewer@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Post not found");

        verifyNoInteractions(s3Service);
        verifyNoInteractions(objectDetectionService);
        verifyNoInteractions(scanSessionService);
    }

    @Test
    void scanPostImageRejectsPostWithoutImage() {
        UUID postId = UUID.randomUUID();
        Post post = post("author@example.com", PostStatus.ACTIVE, null);

        when(profileRepository.findByEmail("viewer@example.com")).thenReturn(Optional.of(profile("viewer@example.com")));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postImageScanService.scanPostImage(postId, "viewer@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Post has no image to scan");

        verify(s3Service, never()).downloadFile(any());
        verifyNoInteractions(objectDetectionService);
        verifyNoInteractions(scanSessionService);
    }

    @Test
    void scanPostImageRejectsImageOutsideConfiguredObjectStore() {
        UUID postId = UUID.randomUUID();
        Post post = post("author@example.com", PostStatus.ACTIVE, "https://other.example.test/images/post.jpg");

        when(profileRepository.findByEmail("viewer@example.com")).thenReturn(Optional.of(profile("viewer@example.com")));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(s3Service.extractKey("https://other.example.test/images/post.jpg")).thenReturn(null);

        assertThatThrownBy(() -> postImageScanService.scanPostImage(postId, "viewer@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Post image is not stored in the configured object store");

        verify(s3Service, never()).downloadFile(any());
        verifyNoInteractions(objectDetectionService);
        verifyNoInteractions(scanSessionService);
    }

    @Test
    void scanPostImageRejectsUnknownUser() {
        UUID postId = UUID.randomUUID();

        when(profileRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postImageScanService.scanPostImage(postId, "missing@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        verify(postRepository, never()).findById(any());
        verifyNoInteractions(s3Service);
    }

    private Profile profile(String email) {
        return Profile.builder()
                .email(email)
                .passwordHash("hash")
                .build();
    }

    private Post post(String authorEmail, PostStatus status, String imageUrl) {
        return Post.builder()
                .author(profile(authorEmail))
                .content("Post content")
                .status(status)
                .imageUrl(imageUrl)
                .build();
    }

    private PostImage postImage(Post post, String imageUrl, int position) {
        return PostImage.builder()
                .post(post)
                .imageUrl(imageUrl)
                .position(position)
                .build();
    }
}
