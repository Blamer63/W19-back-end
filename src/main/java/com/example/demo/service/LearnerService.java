package com.example.demo.service;

import com.example.demo.dto.LanguageInfo;
import com.example.demo.dto.LearnerResponse;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearnerService {

        private final ProfileRepository profileRepository;

        public List<LearnerResponse> findNearbyLearners(
                        Double latitude,
                        Double longitude,
                        Double radiusKm,
                        String languageCode,
                        String currentUserEmail) {

                Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

                List<Profile> nearbyProfiles = profileRepository.findNearbyProfiles(
                                currentUser.getId(),
                                latitude,
                                longitude,
                                radiusKm);

                return nearbyProfiles.stream()
                                // .filter(profile -> !profile.getEmail().equals(currentUserEmail)) // Handled
                                // by DB query
                                .filter(profile -> languageCode == null || hasLanguage(profile, languageCode)) // Filter
                                                                                                               // by
                                                                                                               // language
                                                                                                               // if
                                                                                                               // provided
                                .map(profile -> mapToLearnerResponse(profile, latitude, longitude))
                                .sorted((a, b) -> Double.compare(a.getDistanceKm(), b.getDistanceKm())) // Sort by
                                                                                                        // distance
                                .collect(Collectors.toList());
        }

        private boolean hasLanguage(Profile profile, String languageCode) {
                return profile.getLanguages().stream()
                                .anyMatch(ul -> ul.getLanguage().getCode().equals(languageCode));
        }

        private LearnerResponse mapToLearnerResponse(Profile profile, Double queryLat, Double queryLon) {
                double distance = calculateDistance(queryLat, queryLon, profile.getLatitude(), profile.getLongitude());

                List<LanguageInfo> languages = profile.getLanguages().stream()
                                .map(this::mapToLanguageInfo)
                                .collect(Collectors.toList());

                return LearnerResponse.builder()
                                .id(profile.getId())
                                .displayName(profile.getDisplayName())
                                .avatarUrl(profile.getAvatarUrl())
                                .latitude(profile.getLatitude())
                                .longitude(profile.getLongitude())
                                .distanceKm(Math.round(distance * 100.0) / 100.0) // Round to 2 decimal places
                                .languages(languages)
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
