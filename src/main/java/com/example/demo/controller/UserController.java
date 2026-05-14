package com.example.demo.controller;

import com.example.demo.dto.ProfileResponse;
import com.example.demo.dto.UserSettingsDTO;
import com.example.demo.dto.PostResponse;
import com.example.demo.dto.PrivacySettingsDto;
import com.example.demo.dto.PublicUserProfileDto;
import com.example.demo.service.PostService;
import com.example.demo.service.S3Service;
import com.example.demo.service.UserService;
import com.example.demo.entity.Profile;
import com.example.demo.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

        private final UserService userService;
        private final ProfileRepository profileRepository;
        private final com.example.demo.service.ProfileService profileService;
        private final PostService postService;
        private final S3Service s3Service;

        @GetMapping("/me")
        public ResponseEntity<ProfileResponse> getCurrentUser(Authentication authentication) {
                Profile profile = profileRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));
                return ResponseEntity.ok(profileService.mapToResponse(profile));
        }

        @GetMapping("/{userId}")
        public ResponseEntity<PublicUserProfileDto> getPublicProfile(
                        @PathVariable UUID userId,
                        Authentication authentication) {
                UUID currentUserId = null;
                if (authentication != null && authentication.isAuthenticated()) {
                        // Try to find current user ID
                        try {
                                Profile currentUser = profileRepository.findByEmail(authentication.getName())
                                                .orElse(null);
                                if (currentUser != null) {
                                        currentUserId = currentUser.getId();
                                }
                        } catch (Exception e) {
                                // unexpected authentication state, treat as anonymous
                        }
                }

                return ResponseEntity.ok(userService.getPublicProfile(userId, currentUserId));
        }

        @GetMapping("/{userId}/posts")
        public ResponseEntity<Page<PostResponse>> getUserPosts(
                        @PathVariable UUID userId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        Authentication authentication) {
                String email = authentication != null ? authentication.getName() : null;
                // If email is null, PostService typically requires it for "userReaction".
                // The requirement says "Authorization: Bearer {token}". So we assume
                // authenticated.
                if (email == null) {
                        // If we want to allow public viewing of posts, we need to handle anonymous in
                        // PostService.
                        // Currently PostService looks up user by email.
                        // Let's assume authentication is required as per requirement "Authorization:
                        // Bearer {token}"
                        return ResponseEntity.status(401).build();
                }

                return ResponseEntity.ok(postService.getPostsByUser(userId, email, PageRequest.of(page, size)));
        }

        @PatchMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ProfileResponse> updateProfile(
                        Authentication authentication,
                        @RequestParam(required = false) String displayName,
                        @RequestParam(required = false) String bio,
                        @RequestParam(required = false) String location,
                        @RequestParam(required = false) Double latitude,
                        @RequestParam(required = false) Double longitude,
                        @RequestPart(value = "avatar", required = false) MultipartFile avatar) throws IOException {
                Profile profile = profileRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                if (displayName != null) {
                        profile.setDisplayName(displayName);
                }
                if (bio != null) {
                        profile.setBio(bio);
                }
                if (avatar != null && !avatar.isEmpty()) {
                        s3Service.validateImageFile(avatar);
                        String oldAvatarUrl = profile.getAvatarUrl();
                        String newAvatarUrl = s3Service.uploadFile(avatar, "images");
                        profile.setAvatarUrl(newAvatarUrl);
                        if (oldAvatarUrl != null) {
                                String key = s3Service.extractKey(oldAvatarUrl);
                                if (key != null) {
                                        try {
                                                s3Service.deleteFile(key);
                                        } catch (Exception e) {
                                                log.warn("Failed to delete old avatar from S3: {}", key, e);
                                        }
                                }
                        }
                }
                if (location != null) {
                        profile.setLocation(location);
                }
                if (latitude != null) {
                        profile.setLatitude(latitude);
                }
                if (longitude != null) {
                        profile.setLongitude(longitude);
                }
                Profile updated = profileRepository.save(profile);
                return ResponseEntity.ok(profileService.mapToResponse(updated));
        }

        @GetMapping("/me/settings")
        public ResponseEntity<UserSettingsDTO> getMySettings(Authentication authentication) {
                Profile profile = profileRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));
                return ResponseEntity.ok(userService.getUserSettings(profile.getId()));
        }

        @PatchMapping("/me/settings")
        public ResponseEntity<UserSettingsDTO> updateMySettings(
                        Authentication authentication,
                        @RequestBody UserSettingsDTO settings) {
                Profile profile = profileRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));
                return ResponseEntity.ok(userService.updateUserSettings(profile.getId(), settings));
        }

        @PatchMapping("/me/privacy")
        public ResponseEntity<PrivacySettingsDto> updatePrivacySettings(
                        Authentication authentication,
                        @RequestBody PrivacySettingsDto settings) {
                Profile profile = profileRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));
                return ResponseEntity.ok(userService.updatePrivacySettings(profile.getId(), settings));
        }

        @PostMapping("/{userId}/block")
        public ResponseEntity<Void> blockUser(
                        Authentication authentication,
                        @PathVariable java.util.UUID userId) {
                Profile blocker = profileRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                userService.blockUser(blocker.getId(), userId);
                return ResponseEntity.ok().build();
        }

        @DeleteMapping("/{userId}/block")
        public ResponseEntity<Void> unblockUser(
                        Authentication authentication,
                        @PathVariable java.util.UUID userId) {
                Profile blocker = profileRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                userService.unblockUser(blocker.getId(), userId);
                return ResponseEntity.ok().build();
        }

}
