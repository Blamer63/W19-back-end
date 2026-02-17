package com.example.demo.controller;

import com.example.demo.dto.UserLanguageDTO;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.UserLanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserLanguageController {

        private final ProfileRepository profileRepository;
        private final LanguageRepository languageRepository;
        private final UserLanguageRepository userLanguageRepository;

        @GetMapping("/me/languages")
        public ResponseEntity<List<UserLanguageDTO>> getUserLanguages(Authentication authentication) {
                Profile profile = profileRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                List<UserLanguageDTO> languages = profile.getLanguages().stream()
                                .map(ul -> UserLanguageDTO.builder()
                                                .code(ul.getLanguage().getCode())
                                                .name(ul.getLanguage().getName())
                                                .flagEmoji(ul.getLanguage().getFlagEmoji())
                                                .proficiency(ul.getProficiency())
                                                .isLearning(ul.isLearning())
                                                .build())
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

                // Clear existing languages
                userLanguageRepository.deleteByProfileId(profile.getId());

                // Add new languages
                List<UserLanguage> newLanguages = languages.stream().map(dto -> {
                        Language lang = languageRepository.findByCode(dto.getCode())
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Language not found: " + dto.getCode()));

                        return UserLanguage.builder()
                                        .profile(profile)
                                        .language(lang)
                                        .proficiency(dto.getProficiency())
                                        .isLearning(dto.isLearning())
                                        .build();
                }).collect(Collectors.toList());

                List<UserLanguage> saved = userLanguageRepository.saveAll(newLanguages);

                return ResponseEntity.ok(saved.stream().map(ul -> UserLanguageDTO.builder()
                                .code(ul.getLanguage().getCode())
                                .name(ul.getLanguage().getName())
                                .flagEmoji(ul.getLanguage().getFlagEmoji())
                                .proficiency(ul.getProficiency())
                                .isLearning(ul.isLearning())
                                .build()).collect(Collectors.toList()));
        }
}
