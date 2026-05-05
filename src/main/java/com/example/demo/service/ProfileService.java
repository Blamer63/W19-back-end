package com.example.demo.service;

import com.example.demo.dto.ProfileResponse;
import com.example.demo.dto.UserLanguageDTO;
import com.example.demo.entity.Profile;
import com.example.demo.repository.FollowRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {

        private final ProfileRepository profileRepository;
        private final PostRepository postRepository;
        private final FollowRepository followRepository;

        @Transactional(readOnly = true)
        public List<ProfileResponse> searchProfiles(String nativeLanguage, String learningLanguage) {
                // Note: Repository query still uses old model, this will need a fix next
                return profileRepository.searchProfiles(nativeLanguage, learningLanguage).stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        public ProfileResponse mapToResponse(Profile profile) {
                return ProfileResponse.builder()
                                .id(profile.getId())
                                .username(profile.getUsername())
                                .email(profile.getEmail())
                                .displayName(profile.getDisplayName())
                                .avatarUrl(profile.getAvatarUrl())
                                .bio(profile.getBio())
                                .latitude(profile.getLatitude())
                                .longitude(profile.getLongitude())

                                .createdAt(profile.getCreatedAt() != null ? profile.getCreatedAt()
                                                .atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
                                .languages(profile.getLanguages().stream()
                                                .map(ul -> UserLanguageDTO.builder()
                                                                .code(ul.getLanguage().getCode())
                                                                .name(ul.getLanguage().getName())
                                                                .flagEmoji(ul.getLanguage().getFlagEmoji())
                                                                .proficiency(ul.getProficiency())
                                                                .isLearning(ul.isLearning())
                                                                .build())
                                                .collect(Collectors.toList()))
                                .roles(profile.getRoles().stream()
                                                .map(role -> role.getRole().name())
                                                .collect(Collectors.toList()))
                                .postsCount(postRepository.countByAuthorId(profile.getId()))
                                .followingCount(followRepository.countByFollowerId(profile.getId()))
                                .followersCount(followRepository.countByFollowingId(profile.getId()))
                                .build();
        }
}
