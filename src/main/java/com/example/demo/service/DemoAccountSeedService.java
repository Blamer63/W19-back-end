package com.example.demo.service;

import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.enums.AppRole;
import com.example.demo.enums.LocationVisibility;
import com.example.demo.enums.ProficiencyLevel;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.PracticeResultRepository;
import com.example.demo.repository.SavedWordRepository;
import com.example.demo.repository.UserLanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@org.springframework.context.annotation.Profile("!test")
@RequiredArgsConstructor
public class DemoAccountSeedService {

    private static final String DEMO_PASSWORD = "123456";

    private final ProfileRepository profileRepository;
    private final LanguageRepository languageRepository;
    private final UserLanguageRepository userLanguageRepository;
    private final SavedWordRepository savedWordRepository;
    private final PracticeResultRepository practiceResultRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoLearningSeedService demoLearningSeedService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDemoAccounts() {
        if (languageRepository.findByCode("en").isEmpty()
                || languageRepository.findByCode("es").isEmpty()
                || languageRepository.findByCode("ja").isEmpty()) {
            return;
        }

        Profile spanishDemo = upsertDemoProfile(
                "demo_spanish",
                "demo.spanish@locale.app",
                "Demo Spanish Learner",
                "English speaker learning Spanish for travel, markets, and everyday chats.",
                -33.8688,
                151.2093);
        ensureLanguage(spanishDemo, "en", ProficiencyLevel.NATIVE, false);
        ensureLanguage(spanishDemo, "es", ProficiencyLevel.BEGINNER, true);
        demoLearningSeedService.seedForLearningLanguages(spanishDemo, List.of("es"));

        Profile japaneseDemo = upsertDemoProfile(
                "demo_japanese",
                "demo.japanese@locale.app",
                "Demo Japanese Learner",
                "English speaker learning Japanese with real hiragana, katakana, and kanji.",
                -33.8731,
                151.2065);
        ensureLanguage(japaneseDemo, "en", ProficiencyLevel.NATIVE, false);
        ensureLanguage(japaneseDemo, "ja", ProficiencyLevel.BEGINNER, true);
        demoLearningSeedService.seedForLearningLanguages(japaneseDemo, List.of("ja"));

        cleanupGenericDemoUser();
    }

    private Profile upsertDemoProfile(
            String username,
            String email,
            String displayName,
            String bio,
            double latitude,
            double longitude) {
        Profile profile = profileRepository.findByEmail(email)
                .orElseGet(() -> Profile.builder()
                        .username(username)
                        .email(email)
                        .displayName(displayName)
                        .location("Sydney NSW")
                        .latitude(latitude)
                        .longitude(longitude)
                        .locationVisibility(LocationVisibility.PUBLIC)
                        .showSavedWords(true)
                        .build());

        profile.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        profile.setUsername(username);
        profile.setDisplayName(displayName);
        profile.setBio(bio);
        profile.setLocation("Sydney NSW");
        profile.setLatitude(latitude);
        profile.setLongitude(longitude);
        profile.setLocationVisibility(LocationVisibility.PUBLIC);
        profile.setShowSavedWords(true);

        boolean hasUserRole = profile.getRoles().stream()
                .anyMatch(role -> role.getRole() == AppRole.USER);
        if (!hasUserRole) {
            profile.addRole(AppRole.USER);
        }

        return profileRepository.save(profile);
    }

    private void ensureLanguage(Profile profile, String languageCode, ProficiencyLevel proficiency, boolean isLearning) {
        boolean exists = profile.getLanguages().stream()
                .anyMatch(language -> language.getLanguage() != null
                        && languageCode.equals(language.getLanguage().getCode())
                        && language.isLearning() == isLearning);
        if (exists) {
            return;
        }

        Language language = languageRepository.findByCode(languageCode)
                .orElseThrow(() -> new IllegalStateException("Language not found: " + languageCode));
        UserLanguage userLanguage = UserLanguage.builder()
                .profile(profile)
                .language(language)
                .proficiency(proficiency)
                .isLearning(isLearning)
                .build();
        userLanguageRepository.save(userLanguage);
        profile.getLanguages().add(userLanguage);
    }

    private void cleanupGenericDemoUser() {
        profileRepository.findByEmail("demo@locale.app").ifPresent(profile -> {
            profile.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
            if (savedWordRepository.countByUserIdAndLanguageCode(profile.getId(), "ko") == 0) {
                return;
            }

            practiceResultRepository.deleteBySavedWordUserId(profile.getId());
            savedWordRepository.deleteByUserId(profile.getId());
            userLanguageRepository.deleteByProfileId(profile.getId());
            profile.getLanguages().clear();
            ensureLanguage(profile, "en", ProficiencyLevel.NATIVE, false);
            ensureLanguage(profile, "ja", ProficiencyLevel.BEGINNER, true);
            demoLearningSeedService.seedForLearningLanguages(profile, List.of("ja"));
        });
    }
}
