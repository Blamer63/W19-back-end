package com.example.demo.service;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.ProfileResponse;
import com.example.demo.dto.UserLanguageDTO;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.Profile;

import com.example.demo.repository.ProfileRepository;
import com.example.demo.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

        private final ProfileRepository profileRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtUtils jwtUtils;
        private final AuthenticationManager authenticationManager;
        private final RefreshTokenService refreshTokenService;
        private final com.example.demo.repository.PostRepository postRepository;
        private final com.example.demo.repository.FollowRepository followRepository;

        @org.springframework.beans.factory.annotation.Value("${app.jwt.expiration-ms}")
        private Long jwtExpirationMs;

        public AuthResponse login(LoginRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getEmail(),
                                                request.getPassword()));
                Profile user = profileRepository.findByEmail(request.getEmail())
                                .orElseThrow();
                String jwtToken = jwtUtils.generateToken(user.getEmail());
                com.example.demo.entity.RefreshToken refreshToken = refreshTokenService
                                .createRefreshToken(user.getId());

                return AuthResponse.builder()
                                .userId(user.getId())
                                .accessToken(jwtToken)
                                .refreshToken(refreshToken.getToken())
                                .expiresIn(jwtExpirationMs / 1000)
                                .build();
        }

        @Transactional
        public AuthResponse register(RegisterRequest request) {
                if (profileRepository.existsByEmail(request.getEmail())) {
                        throw new RuntimeException("Email already taken");
                }

                String username = request.getUsername();
                if (username == null || username.isBlank()) {
                        username = request.getEmail().split("@")[0];
                }

                String baseUsername = username;
                int attempt = 0;
                while (profileRepository.existsByUsername(username)) {
                        attempt++;
                        username = baseUsername + attempt;
                        if (attempt > 99) {
                                username = baseUsername + java.util.UUID.randomUUID().toString().substring(0, 6);
                                break;
                        }
                }

                Profile profile = new Profile();
                profile.setEmail(request.getEmail());
                profile.setUsername(username);
                profile.setDisplayName(request.getDisplayName());
                profile.setPasswordHash(passwordEncoder.encode(request.getPassword()));

                profile.addRole(com.example.demo.enums.AppRole.USER);

                Profile savedProfile = profileRepository.save(profile);

                // Note: RegisterRequest still has old language fields?
                // Should probably update RegisterRequest or handle it here
                // For now, just save the profile.

                String jwtToken = jwtUtils.generateToken(savedProfile.getEmail());
                com.example.demo.entity.RefreshToken refreshToken = refreshTokenService
                                .createRefreshToken(savedProfile.getId());

                return AuthResponse.builder()
                                .userId(savedProfile.getId())
                                .accessToken(jwtToken)
                                .refreshToken(refreshToken.getToken())
                                .expiresIn(jwtExpirationMs / 1000)
                                .build();
        }

        @Transactional(readOnly = true)
        public ProfileResponse getProfileByEmail(String email) {
                Profile profile = profileRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("Profile not found"));

                return mapToResponse(profile);
        }

        private ProfileResponse mapToResponse(Profile profile) {
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
                                .followersCount(followRepository.countByFollowingId(profile.getId()))
                                .followingCount(followRepository.countByFollowerId(profile.getId()))
                                .build();
        }

        @Transactional
        public AuthResponse refreshToken(com.example.demo.dto.TokenRefreshRequest request) {
                String requestRefreshToken = request.getRefreshToken();

                return refreshTokenService.findByToken(requestRefreshToken)
                                .map(refreshTokenService::verifyExpiration)
                                .map(com.example.demo.entity.RefreshToken::getProfile)
                                .map(user -> {
                                        String token = jwtUtils.generateToken(user.getEmail());
                                        // Rotate refresh token (optional but recommended)
                                        com.example.demo.entity.RefreshToken newRefreshToken = refreshTokenService
                                                        .createRefreshToken(user.getId());
                                        return AuthResponse.builder()
                                                        .userId(user.getId())
                                                        .accessToken(token)
                                                        .refreshToken(newRefreshToken.getToken())
                                                        .expiresIn(jwtExpirationMs / 1000)
                                                        .build();
                                })
                                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
        }

        @Transactional
        public ProfileResponse updateProfile(String email, java.util.Map<String, Object> updates) {
                Profile profile = profileRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("Profile not found"));

                if (updates.containsKey("displayName")) {
                        profile.setDisplayName((String) updates.get("displayName"));
                }
                if (updates.containsKey("bio")) {
                        profile.setBio((String) updates.get("bio"));
                }
                if (updates.containsKey("avatarUrl")) {
                        profile.setAvatarUrl((String) updates.get("avatarUrl"));
                }

                return mapToResponse(profileRepository.save(profile));
        }

        @Transactional
        public void logout(String email) {
                Profile user = profileRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("Profile not found"));
                refreshTokenService.deleteByProfileId(user.getId());
        }
}
