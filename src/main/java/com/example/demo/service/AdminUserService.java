package com.example.demo.service;

import com.example.demo.dto.AdminSeedRunResponse;
import com.example.demo.dto.AdminUserLanguageResponse;
import com.example.demo.dto.AdminUserPageResponse;
import com.example.demo.dto.AdminUserResponse;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.service.StarterSeedRunService.SeedRunStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final ProfileRepository profileRepository;
    private final PostRepository postRepository;
    private final StarterSeedRunService starterSeedRunService;

    @Transactional(readOnly = true)
    public AdminUserPageResponse listUsers(String query, Pageable pageable) {
        String normalized = (query == null || query.isBlank()) ? null : query.trim();
        Page<Profile> page = (normalized == null)
                ? profileRepository.findAll(pageable)
                : profileRepository.searchForAdmin(normalized, pageable);
        List<AdminUserResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return AdminUserPageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toResponse(profile);
    }

    /**
     * Resets the user's starter-seed marker to PENDING and immediately re-runs seeding for their
     * current learning languages. resetToPending() and seedIfPending() each run in their own
     * REQUIRES_NEW transaction, so the reset is committed and visible before seeding acquires it.
     */
    @Transactional
    public AdminUserResponse reseedUser(UUID profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!starterSeedRunService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Starter seeding is disabled (app.starter-seed.enabled=false)");
        }

        List<String> learningLanguageCodes = profile.getLanguages().stream()
                .filter(UserLanguage::isLearning)
                .map(userLanguage -> userLanguage.getLanguage().getCode())
                .toList();

        starterSeedRunService.resetToPending(profileId);
        try {
            starterSeedRunService.seedIfPending(profile, learningLanguageCodes);
        } catch (Exception e) {
            log.error("starter-seed: admin re-seed failed for profile {}", profileId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Re-seed failed; marker left PENDING for retry", e);
        }
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public List<AdminSeedRunResponse> listSeedRuns() {
        return starterSeedRunService.findAllStatuses().stream()
                .map(run -> {
                    AdminSeedRunResponse response = toSeedRun(run);
                    profileRepository.findById(run.profileId()).ifPresent(profile -> {
                        response.setUsername(profile.getUsername());
                        response.setEmail(profile.getEmail());
                    });
                    return response;
                })
                .toList();
    }

    private AdminUserResponse toResponse(Profile profile) {
        List<AdminUserLanguageResponse> languages = profile.getLanguages().stream()
                .filter(userLanguage -> userLanguage.getLanguage() != null)
                .map(userLanguage -> AdminUserLanguageResponse.builder()
                        .code(userLanguage.getLanguage().getCode())
                        .name(userLanguage.getLanguage().getName())
                        .learning(userLanguage.isLearning())
                        .build())
                .toList();

        List<String> roles = profile.getRoles().stream()
                .filter(userRole -> userRole.getRole() != null)
                .map(userRole -> userRole.getRole().name())
                .distinct()
                .toList();

        AdminSeedRunResponse seedStatus = starterSeedRunService.findStatus(profile.getId())
                .map(this::toSeedRun)
                .orElse(null);

        return AdminUserResponse.builder()
                .id(profile.getId())
                .username(profile.getUsername())
                .email(profile.getEmail())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .bio(profile.getBio())
                .location(profile.getLocation())
                .roles(roles)
                .languages(languages)
                .postsCount(postRepository.countByAuthorId(profile.getId()))
                .createdAt(profile.getCreatedAt())
                .seedStatus(seedStatus)
                .build();
    }

    private AdminSeedRunResponse toSeedRun(SeedRunStatus run) {
        return AdminSeedRunResponse.builder()
                .profileId(run.profileId())
                .seedKey(run.seedKey())
                .status(run.status())
                .createdAt(run.createdAt())
                .completedAt(run.completedAt())
                .build();
    }
}
