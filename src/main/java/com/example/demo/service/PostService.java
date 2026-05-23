package com.example.demo.service;

import com.example.demo.dto.PostResponse;
import com.example.demo.entity.Post;
import com.example.demo.entity.Language;
import com.example.demo.entity.PostImage;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.enums.LocationVisibility;
import com.example.demo.enums.PostStatus;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.*;
import com.example.demo.repository.SavedPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

        private final PostRepository postRepository;
        private final ProfileRepository profileRepository;
        private final PostReactionRepository postReactionRepository;
        private final SavedPostRepository savedPostRepository;
        private final PostCommentRepository postCommentRepository;
        private final S3Service s3Service;
        private static final int MAX_POST_IMAGES = 3;

        @Transactional(readOnly = true)
        public Page<PostResponse> getFeed(String language, Pageable pageable, Double lat, Double lon,
                        String currentUserEmail) {
                Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                List<PostStatus> visibleStatuses = List.of(PostStatus.ACTIVE, PostStatus.APPROVED);

                Page<Post> posts;
                if (language != null && !language.equalsIgnoreCase("all")) {
                        posts = postRepository.findByOriginalLanguageAndStatusIn(language, visibleStatuses, pageable);
                } else {
                        posts = postRepository.findByStatusIn(visibleStatuses, pageable);
                }

                return posts.map(post -> mapToResponse(post, currentUser, lat, lon));
        }

        @Transactional
        public PostResponse createPost(String content, String originalLanguage,
                        Double latitude, Double longitude,
                        MultipartFile image, List<MultipartFile> images, String currentUserEmail) throws IOException {
                Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                List<MultipartFile> imageFiles = normalizePostImages(image, images);
                List<String> imageUrls = new ArrayList<>();
                for (MultipartFile imageFile : imageFiles) {
                        s3Service.validateImageFile(imageFile);
                        imageUrls.add(s3Service.uploadFile(imageFile, "images"));
                }
                String imageUrl = imageUrls.isEmpty() ? null : imageUrls.get(0);

                try {
                        Post post = Post.builder()
                                        .author(currentUser)
                                        .content(content)
                                        .originalLanguage(originalLanguage)
                                        .imageUrl(imageUrl)
                                        .latitude(latitude)
                                        .longitude(longitude)
                                        .status(PostStatus.ACTIVE)
                                        .build();
                        for (int i = 0; i < imageUrls.size(); i++) {
                                post.getImages().add(PostImage.builder()
                                                .post(post)
                                                .imageUrl(imageUrls.get(i))
                                                .position(i)
                                                .build());
                        }
                        return mapToResponse(postRepository.save(post), currentUser, latitude, longitude);
                } catch (Exception e) {
                        for (String uploadedImageUrl : imageUrls) {
                                String key = s3Service.extractKey(uploadedImageUrl);
                                if (key != null) {
                                        try {
                                                s3Service.deleteFile(key);
                                        } catch (Exception ex) {
                                                log.warn("Failed to clean up S3 image after failed post save: {}", key, ex);
                                        }
                                }
                        }
                        throw e;
                }
        }

        @Transactional
        public PostResponse createPost(String content, String originalLanguage,
                        Double latitude, Double longitude,
                        MultipartFile image, String currentUserEmail) throws IOException {
                return createPost(content, originalLanguage, latitude, longitude, image, null, currentUserEmail);
        }

        @Transactional(readOnly = true)
        public PostResponse getPost(UUID postId, String currentUserEmail) {
                Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                Post post = postRepository.findById(postId)
                                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

                return mapToResponse(post, currentUser, null, null);
        }

        @Transactional
        public void deletePost(UUID postId, String currentUserEmail) {
                Post post = postRepository.findById(postId)
                                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

                if (!post.getAuthor().getEmail().equals(currentUserEmail)) {
                        throw new IllegalArgumentException("Unauthorized to delete this post");
                }

                List<String> imageUrls = resolveImageUrls(post);
                postRepository.delete(post);

                for (String imageUrl : imageUrls) {
                        String key = s3Service.extractKey(imageUrl);
                        if (key != null) {
                                try {
                                        s3Service.deleteFile(key);
                                } catch (Exception e) {
                                        log.warn("Failed to delete post image from S3: {}", key, e);
                                }
                        }
                }
        }

        @Transactional(readOnly = true)
        public Page<PostResponse> getSavedPosts(String currentUserEmail, Pageable pageable) {
                Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                Page<com.example.demo.entity.SavedPost> saved = savedPostRepository
                                .findByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable);
                return saved.map(sp -> mapToResponse(sp.getPost(), currentUser, null, null));
        }

        @Transactional(readOnly = true)
        public Page<PostResponse> getPostsByUser(UUID userId, String currentUserEmail, Pageable pageable) {
                Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                if (!profileRepository.existsById(userId)) {
                        throw new ResourceNotFoundException("User not found");
                }

                Page<Post> posts = postRepository.findByAuthorId(userId, pageable);

                return posts.map(post -> mapToResponse(post, currentUser, null, null));
        }

        private List<MultipartFile> normalizePostImages(MultipartFile image, List<MultipartFile> images) {
                List<MultipartFile> imageFiles = new ArrayList<>();
                if (image != null && !image.isEmpty()) {
                        imageFiles.add(image);
                }
                if (images != null) {
                        images.stream()
                                        .filter(file -> file != null && !file.isEmpty())
                                        .forEach(imageFiles::add);
                }
                if (imageFiles.size() > MAX_POST_IMAGES) {
                        throw new IllegalArgumentException("Posts can include up to 3 images");
                }
                return imageFiles;
        }

        private List<String> resolveImageUrls(Post post) {
                List<String> imageUrls = post.getImages() == null ? List.of() : post.getImages().stream()
                                .sorted(Comparator.comparingInt(PostImage::getPosition))
                                .map(PostImage::getImageUrl)
                                .toList();
                if (!imageUrls.isEmpty()) {
                        return imageUrls;
                }
                return post.getImageUrl() == null ? List.of() : List.of(post.getImageUrl());
        }

        private PostResponse mapToResponse(Post post, Profile currentUser, Double lat, Double lon) {
                String distance = "Unknown";
                if (lat != null && lon != null && post.getLatitude() != null && post.getLongitude() != null) {
                        double d = calculateDistance(lat, lon, post.getLatitude(), post.getLongitude());
                        distance = String.format("%.1f km", d);
                }

                String userReaction = postReactionRepository.findByPostIdAndProfileId(post.getId(), currentUser.getId())
                                .map(reaction -> reaction.getType().name())
                                .orElse(null);
                List<String> imageUrls = resolveImageUrls(post);
                String primaryImageUrl = imageUrls.isEmpty() ? null : imageUrls.get(0);

                // Get author's native language for display
                Profile author = post.getAuthor();
                String authorLanguageName = "Unknown";
                String authorFlagEmoji = "🌍";

                // Try to get the author's native language from their UserLanguage settings
                if (author.getLanguages() != null && !author.getLanguages().isEmpty()) {
                        // Find the native language (isLearning = false means it's a native language)
                        UserLanguage nativeLanguage = author.getLanguages().stream()
                                        .filter(ul -> !ul.isLearning())
                                        .findFirst()
                                        .orElse(author.getLanguages().get(0));

                        if (nativeLanguage != null && nativeLanguage.getLanguage() != null) {
                                Language lang = nativeLanguage.getLanguage();
                                authorLanguageName = lang.getName() != null ? lang.getName() : "Unknown";
                                authorFlagEmoji = lang.getFlagEmoji() != null ? lang.getFlagEmoji() : "🌍";
                        }
                }

                return PostResponse.builder()
                                .id(post.getId())
                                .author(PostResponse.AuthorDTO.builder()
                                                .id(author.getId())
                                                .username(author.getUsername() != null ? author.getUsername()
                                                                : "user_" + author.getId().toString().substring(0, 8))
                                                .displayName(author.getDisplayName() != null ? author.getDisplayName()
                                                                : (author.getUsername() != null ? author.getUsername()
                                                                                : "Anonymous"))
                                                .avatarUrl(author.getAvatarUrl())
                                                .language(authorLanguageName)
                                                .flagEmoji(authorFlagEmoji)
                                                .location(author.getLocationVisibility() != LocationVisibility.FRIENDS_ONLY
                                                                && author.getLocationVisibility() != LocationVisibility.NOBODY
                                                                ? author.getLocation()
                                                                : null)
                                                .learningLanguages(
                                                                author.getLanguages().stream()
                                                                                .filter(UserLanguage::isLearning)
                                                                                .map(ul -> (PostResponse.LearningLanguage) PostResponse.LearningLanguage.builder()
                                                                                                .code(ul.getLanguage().getCode())
                                                                                                .name(ul.getLanguage().getName())
                                                                                                .flagEmoji(ul.getLanguage().getFlagEmoji())
                                                                                                .build())
                                                                                .toList())
                                                .build())
                                .content(post.getContent())
                                .originalLanguage(post.getOriginalLanguage())
                                .imageUrl(primaryImageUrl)
                                .imageUrls(imageUrls)
                                .latitude(post.getLatitude())
                                .longitude(post.getLongitude())
                                .location(post.getLatitude() != null ? "Nearby" : "Unknown")
                                .distance(distance)
                                .status(post.getStatus() != null ? post.getStatus().name() : "ACTIVE")
                                .reactions(PostResponse.ReactionSummaryDTO.builder()
                                                .likes((int) postReactionRepository.countByPostId(post.getId()))
                                                .comments((int) postCommentRepository.countByPostId(post.getId()))
                                                .build())
                                .userReaction(userReaction)
                                .isSaved(currentUser != null &&
                                                savedPostRepository.existsByUserIdAndPostId(currentUser.getId(), post.getId()))
                                .createdAt(post.getCreatedAt())
                                .build();
        }

        private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
                double R = 6371; // Earth radius in km
                double dLat = Math.toRadians(lat2 - lat1);
                double dLon = Math.toRadians(lon2 - lon1);
                double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                                                Math.sin(dLon / 2) * Math.sin(dLon / 2);
                double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
                return R * c;
        }
}
