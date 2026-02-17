package com.example.demo.service;

import com.example.demo.dto.CreatePostRequest;
import com.example.demo.dto.PostResponse;
import com.example.demo.entity.Post;
import com.example.demo.entity.Profile;
import com.example.demo.entity.Language;
import com.example.demo.entity.UserLanguage;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

        private final PostRepository postRepository;
        private final ProfileRepository profileRepository;
        private final PostReactionRepository postReactionRepository;
        private final PostCommentRepository postCommentRepository;
        private final LanguageRepository languageRepository;

        @Transactional(readOnly = true)
        public Page<PostResponse> getFeed(String language, Pageable pageable, Double lat, Double lon,
                        String currentUserEmail) {
                Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // TEMPORARY: Show all posts for debugging
                // TODO: Re-enable status filtering after fixing post statuses in database
                /*
                 * java.util.List<com.example.demo.enums.PostStatus> visibleStatuses =
                 * java.util.Arrays.asList(
                 * com.example.demo.enums.PostStatus.ACTIVE,
                 * com.example.demo.enums.PostStatus.APPROVED
                 * );
                 */

                Page<Post> posts;
                if (language != null && !language.equalsIgnoreCase("all")) {
                        posts = postRepository.findByOriginalLanguage(language, pageable);
                        // posts = postRepository.findByOriginalLanguageAndStatusIn(language,
                        // visibleStatuses, pageable);
                } else {
                        posts = postRepository.findAll(pageable);
                        // posts = postRepository.findByStatusIn(visibleStatuses, pageable);
                }

                log.info("DEBUG: Found {} posts in database for user {}", posts.getTotalElements(), currentUserEmail);
                log.info("DEBUG: Language filter: {}", language);

                try {
                        Page<PostResponse> response = posts.map(post -> mapToResponse(post, currentUser, lat, lon));
                        log.info("DEBUG: Successfully mapped {} posts", response.getNumberOfElements());
                        return response;
                } catch (Exception e) {
                        log.error("DEBUG: Error mapping posts to response", e);
                        throw e;
                }
        }

        @Transactional
        public PostResponse createPost(CreatePostRequest request, String currentUserEmail) {
                Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Post post = Post.builder()
                                .author(currentUser)
                                .content(request.getContent())
                                .originalLanguage(request.getOriginalLanguage())
                                .imageUrl(request.getImageUrl())
                                .latitude(request.getLatitude())
                                .longitude(request.getLongitude())
                                .build();

                return mapToResponse(postRepository.save(post), currentUser, request.getLatitude(),
                                request.getLongitude());
        }

        @Transactional(readOnly = true)
        public PostResponse getPost(UUID postId, String currentUserEmail) {
                Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Post post = postRepository.findById(postId)
                                .orElseThrow(() -> new RuntimeException("Post not found"));

                return mapToResponse(post, currentUser, null, null);
        }

        @Transactional
        public void deletePost(UUID postId, String currentUserEmail) {
                Post post = postRepository.findById(postId)
                                .orElseThrow(() -> new RuntimeException("Post not found"));

                if (!post.getAuthor().getEmail().equals(currentUserEmail)) {
                        throw new RuntimeException("Unauthorized to delete this post");
                }

                postRepository.delete(post);
        }

        @Transactional(readOnly = true)
        public Page<PostResponse> getPostsByUser(UUID userId, String currentUserEmail, Pageable pageable) {
                Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                if (!profileRepository.existsById(userId)) {
                        throw new RuntimeException("User not found");
                }

                Page<Post> posts = postRepository.findByAuthorIdAndStatus(userId,
                                com.example.demo.enums.PostStatus.APPROVED, pageable);

                return posts.map(post -> mapToResponse(post, currentUser, null, null));
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
                                                .build())
                                .content(post.getContent())
                                .originalLanguage(post.getOriginalLanguage())
                                .imageUrl(post.getImageUrl())
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
