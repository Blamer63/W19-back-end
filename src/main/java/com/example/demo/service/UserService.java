package com.example.demo.service;

import com.example.demo.dto.UserSettingsDTO;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserBlock;
import com.example.demo.entity.UserSettings;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.PrivacySettingsDto;
import com.example.demo.dto.PublicUserProfileDto;
import com.example.demo.dto.UserLanguageDTO;
import com.example.demo.enums.PostStatus;
import com.example.demo.repository.FollowRepository;
import com.example.demo.repository.PostRepository;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserSettingsRepository userSettingsRepository;
    private final ProfileRepository profileRepository;
    private final com.example.demo.repository.UserBlockRepository userBlockRepository;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;

    @Transactional(readOnly = true)
    public UserSettingsDTO getUserSettings(UUID userId) {
        UserSettings settings = userSettingsRepository.findByProfileId(userId)
                .orElse(getSafeDefaultSettings(userId));

        return UserSettingsDTO.builder()
                .notificationPrefs(settings.getNotificationPrefs())
                .privacySettings(settings.getPrivacySettings())
                .theme(settings.getTheme())
                .build();
    }

    @Transactional
    public UserSettingsDTO updateUserSettings(UUID userId, UserSettingsDTO updates) {
        UserSettings settings = userSettingsRepository.findByProfileId(userId)
                .orElse(getSafeDefaultSettings(userId));

        if (updates.getNotificationPrefs() != null) {
            settings.setNotificationPrefs(updates.getNotificationPrefs());
        }
        if (updates.getPrivacySettings() != null) {
            settings.setPrivacySettings(updates.getPrivacySettings());
        }
        if (updates.getTheme() != null) {
            settings.setTheme(updates.getTheme());
        }

        UserSettings saved = userSettingsRepository.save(settings);
        return UserSettingsDTO.builder()
                .notificationPrefs(saved.getNotificationPrefs())
                .privacySettings(saved.getPrivacySettings())
                .theme(saved.getTheme())
                .build();
    }

    private UserSettings getSafeDefaultSettings(UUID userId) {
        // If settings don't exist, create default ones linked to profile
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserSettings newSettings = UserSettings.builder()
                .profile(profile)
                .build();

        return userSettingsRepository.save(newSettings);
    }

    @Transactional
    public void blockUser(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new RuntimeException("Cannot block yourself");
        }

        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new RuntimeException("User already blocked");
        }

        Profile blocker = profileRepository.findById(blockerId)
                .orElseThrow(() -> new RuntimeException("Blocker not found"));
        Profile blocked = profileRepository.findById(blockedId)
                .orElseThrow(() -> new RuntimeException("Blocked user not found"));

        UserBlock block = UserBlock.builder()
                .blocker(blocker)
                .blocked(blocked)
                .build();

        userBlockRepository.save(block);

        // TODO: Also unfollow if following?
    }

    @Transactional
    public void unblockUser(UUID blockerId, UUID blockedId) {
        UserBlock block = userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseThrow(() -> new RuntimeException("Block not found"));

        userBlockRepository.delete(block);
    }

    public PublicUserProfileDto getPublicProfile(UUID userId, UUID currentUserId) {
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isFollowing = false;
        if (currentUserId != null) {
            isFollowing = followRepository.existsByFollowerIdAndFollowingId(currentUserId, userId);
        }

        // Use counts from Profile entity if maintained, or query repositories if
        // real-time needed.
        // The implementation plan suggested adding count columns to Profile (Option B
        // in requirements).
        // However, we didn't implement the triggers yet. Standard JPA approach is
        // queries.
        // But we added the columns to Profile entity!
        // Let's assume for now we use the columns in Profile,
        // BUT they might be 0 if not updated. The requirement said "Efficient Count
        // Queries".
        // Let's query the repositories to be safe and accurate for now, or use the
        // fields if we implemented the triggers?
        // We haven't implemented triggers. Let's use repository counts and populate the
        // DTO.
        // Actually, the requirements said: "Option A: Real-time counts (simpler, slower
        // for large datasets)"
        // "Option B: Denormalized counters (faster, requires triggers)"
        // I added the columns on Profile. I should probably trust the columns if we are
        // going with Option B.
        // But since I didn't add triggers, they will be stale.
        // Let's use repository counts to populate the DTO for now.

        long followersCount = followRepository.countByFollowingId(userId);
        long followingCount = followRepository.countByFollowerId(userId);
        long postsCount = postRepository.countByAuthorIdAndStatus(userId, PostStatus.APPROVED);

        return PublicUserProfileDto.builder()
                .id(profile.getId())
                .username(profile.getUsername())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .bio(profile.getBio())
                .location(profile.getLocation())
                .createdAt(profile.getCreatedAt() != null
                        ? profile.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
                        : null)
                .languages(profile.getLanguages().stream()
                        .map(ul -> UserLanguageDTO.builder()
                                .code(ul.getLanguage().getCode())
                                .name(ul.getLanguage().getName())
                                .flagEmoji(ul.getLanguage().getFlagEmoji())
                                .proficiency(ul.getProficiency())
                                .isLearning(ul.isLearning())
                                .build())
                        .collect(Collectors.toList()))
                .followersCount((int) followersCount)
                .followingCount((int) followingCount)
                .postsCount((int) postsCount)
                .isFollowing(isFollowing)
                .privacySettings(PrivacySettingsDto.builder()
                        .showActivity(profile.isShowActivity())
                        .showSavedWords(profile.isShowSavedWords())
                        .build())
                .build();
    }

    @Transactional
    public PrivacySettingsDto updatePrivacySettings(UUID userId, PrivacySettingsDto settings) {
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (settings.getShowActivity() != null) {
            profile.setShowActivity(settings.getShowActivity());
        }
        if (settings.getShowSavedWords() != null) {
            profile.setShowSavedWords(settings.getShowSavedWords());
        }

        Profile saved = profileRepository.save(profile);

        return PrivacySettingsDto.builder()
                .showActivity(saved.isShowActivity())
                .showSavedWords(saved.isShowSavedWords())
                .build();
    }
}
