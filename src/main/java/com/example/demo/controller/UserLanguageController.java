package com.example.demo.controller;

import com.example.demo.dto.UserLanguageDTO;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.UserLanguageRepository;
import com.example.demo.service.StarterSeedRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserLanguageController {

        private final ProfileRepository profileRepository;
        private final LanguageRepository languageRepository;
        private final UserLanguageRepository userLanguageRepository;
        private final StarterSeedRunService starterSeedRunService;

        @GetMapping("/me/languages")
        public ResponseEntity<List<UserLanguageDTO>> getUserLanguages(Authentication authentication) {
                Profile profile = profileRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                List<UserLanguageDTO> languages = profile.getLanguages().stream()
                                .map(this::toDto)
                                .collect(Collectors.toMap(
                                                dto -> normalizeLanguageCode(dto.getCode()),
                                                dto -> dto,
                                                (first, ignored) -> first,
                                                LinkedHashMap::new))
                                .values()
                                .stream()
                                .collect(Collectors.toList());

                return ResponseEntity.ok(languages);
        }

        @PutMapping("/me/languages")
        @Transactional
        public ResponseEntity<List<UserLanguageDTO>> updateUserLanguages(
                        Authentication authentication,
                        @RequestBody List<UserLanguageDTO> languages) {

                Profile profile = profileRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                List<UserLanguageDTO> uniqueLanguages = languages.stream()
                                .filter(dto -> dto.getCode() != null && !dto.getCode().isBlank())
                                .collect(Collectors.toMap(
                                                dto -> normalizeLanguageCode(dto.getCode()),
                                                dto -> dto,
                                                (first, ignored) -> first,
                                                LinkedHashMap::new))
                                .values()
                                .stream()
                                .collect(Collectors.toList());

                // Clear existing languages
                userLanguageRepository.deleteByProfileId(profile.getId());

                // Add new languages
                List<UserLanguage> newLanguages = uniqueLanguages.stream().map(dto -> {
                        String languageCode = normalizeLanguageCode(dto.getCode());
                        Language lang = languageRepository.findByCode(languageCode)
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Language not found: " + languageCode));

                        return UserLanguage.builder()
                                        .profile(profile)
                                        .language(lang)
                                        .proficiency(dto.getProficiency())
                                        .isLearning(dto.isLearning())
                                        .build();
                }).collect(Collectors.toList());

                List<UserLanguage> saved = userLanguageRepository.saveAll(newLanguages);
                List<String> learningLanguageCodes = saved.stream()
                                .filter(UserLanguage::isLearning)
                                .map(userLanguage -> userLanguage.getLanguage().getCode())
                                .collect(Collectors.toList());
                starterSeedRunService.seedIfPending(profile, learningLanguageCodes);

                return ResponseEntity.ok(saved.stream().map(this::toDto).collect(Collectors.toList()));
        }

        private UserLanguageDTO toDto(UserLanguage userLanguage) {
                return UserLanguageDTO.builder()
                                .code(userLanguage.getLanguage().getCode())
                                .name(userLanguage.getLanguage().getName())
                                .flagEmoji(userLanguage.getLanguage().getFlagEmoji())
                                .proficiency(userLanguage.getProficiency())
                                .isLearning(userLanguage.isLearning())
                                .build();
        }

        private String normalizeLanguageCode(String code) {
                return code.trim().toLowerCase(Locale.ROOT);
        }
}
