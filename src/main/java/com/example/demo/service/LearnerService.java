package com.example.demo.service;

import com.example.demo.dto.LanguageInfo;
import com.example.demo.dto.LearnerResponse;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.enums.LocationVisibility;
import com.example.demo.repository.FriendRepository;
import com.example.demo.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearnerService {

        private final ProfileRepository profileRepository;
        private final FriendRepository friendRepository;

        public List<LearnerResponse> findNearbyLearners(
                        Double latitude,
                        Double longitude,
                        Double radiusKm,
                        String languageCode,
                        String currentUserEmail) {

                Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

                List<Profile> profiles;
                boolean hasLocation = latitude != null && longitude != null;

                if (hasLocation) {
                        profiles = profileRepository.findNearbyProfiles(
                                        currentUser.getId(),
                                        latitude,
                                        longitude,
                                        radiusKm);
                } else {
                        // No location provided — return all learners globally
                        profiles = profileRepository.findAllLearnersExcept(currentUser.getId());
                }

                // Get the current user's accepted friend IDs for FRIENDS_ONLY visibility check
                Set<UUID> friendIds = Set.copyOf(friendRepository.findAcceptedFriendIds(currentUser.getId()));

                final Double queryLat = latitude != null ? latitude : 0.0;
                final Double queryLon = longitude != null ? longitude : 0.0;

                String normalizedLanguageCode = normalizeLanguageCode(languageCode);
                Set<String> defaultLearningLanguages = currentUser.getLanguages().stream()
                                .filter(UserLanguage::isLearning)
                                .map(ul -> ul.getLanguage().getCode())
                                .collect(Collectors.toSet());

                return profiles.stream()
                                .filter(profile -> shouldIncludeForLanguage(profile, normalizedLanguageCode,
                                                defaultLearningLanguages))
                                .filter(profile -> isVisibleTo(profile, friendIds))
                                .map(profile -> mapToLearnerResponse(profile, queryLat, queryLon))
                                .sorted((a, b) -> Double.compare(a.getDistanceKm(), b.getDistanceKm()))
                                .collect(Collectors.toList());
        }

        /**
         * Checks if a user's location should be visible on the map.
         * PUBLIC → always shown
         * FRIENDS_ONLY → shown only if the viewer is a mutual friend
         * NOBODY → never shown
         */
        private boolean isVisibleTo(Profile profile, Set<UUID> viewerFriendIds) {
                LocationVisibility visibility = profile.getLocationVisibility();
                if (visibility == null)
                        return true; // treat null as PUBLIC
                return switch (visibility) {
                        case PUBLIC -> true;
                        case FRIENDS_ONLY -> viewerFriendIds.contains(profile.getId());
                        case NOBODY -> false;
                };
        }

        private boolean hasLanguage(Profile profile, String languageCode) {
                return profile.getLanguages().stream()
                                .anyMatch(ul -> ul.getLanguage().getCode().equals(languageCode));
        }

        private boolean shouldIncludeForLanguage(Profile profile, String requestedLanguageCode,
                        Set<String> defaultLearningLanguages) {
                if (requestedLanguageCode != null && !requestedLanguageCode.equals("all")) {
                        return hasLanguage(profile, requestedLanguageCode);
                }
                return defaultLearningLanguages.isEmpty()
                                || profile.getLanguages().stream()
                                                .anyMatch(ul -> defaultLearningLanguages.contains(ul.getLanguage().getCode()));
        }

        private String normalizeLanguageCode(String languageCode) {
                return languageCode == null || languageCode.isBlank()
                                ? null
                                : languageCode.trim().toLowerCase(Locale.ROOT);
        }

        private LearnerResponse mapToLearnerResponse(Profile profile, Double queryLat, Double queryLon) {
                double distance = calculateDistance(queryLat, queryLon, profile.getLatitude(), profile.getLongitude());

                List<LanguageInfo> languages = profile.getLanguages().stream()
                                .map(this::mapToLanguageInfo)
                                .collect(Collectors.toList());

                List<LanguageInfo> learningLanguages = languages.stream()
                                .filter(l -> l.getIsLearning() != null && l.getIsLearning())
                                .collect(Collectors.toList());

                return LearnerResponse.builder()
                                .id(profile.getId())
                                .displayName(profile.getDisplayName())
                                .avatarUrl(profile.getAvatarUrl())
                                .latitude(profile.getLatitude())
                                .longitude(profile.getLongitude())
                                .distanceKm(Math.round(distance * 100.0) / 100.0) // Round to 2 decimal places
                                .languages(languages)
                                .learningLanguages(learningLanguages)
                                .build();
        }

        private LanguageInfo mapToLanguageInfo(UserLanguage userLanguage) {
                return LanguageInfo.builder()
                                .code(userLanguage.getLanguage().getCode())
                                .name(userLanguage.getLanguage().getName())
                                .flagEmoji(userLanguage.getLanguage().getFlagEmoji())
                                .proficiency(userLanguage.getProficiency() != null
                                                ? userLanguage.getProficiency().name()
                                                : null)
                                .isLearning(userLanguage.isLearning())
                                .build();
        }

        /**
         * Calculate distance between two points using Haversine formula
         * 
         * @return distance in kilometers
         */
        private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
                final int EARTH_RADIUS_KM = 6371;

                double dLat = Math.toRadians(lat2 - lat1);
                double dLon = Math.toRadians(lon2 - lon1);

                double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                                                Math.sin(dLon / 2) * Math.sin(dLon / 2);

                double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

                return EARTH_RADIUS_KM * c;
        }
}
