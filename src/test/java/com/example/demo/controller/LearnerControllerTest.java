package com.example.demo.controller;

import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.enums.LocationVisibility;
import com.example.demo.enums.ProficiencyLevel;
import com.example.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class LearnerControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ProfileRepository profileRepository;

        @Autowired
        private LanguageRepository languageRepository;

        @Autowired
        private UserLanguageRepository userLanguageRepository;

        @Autowired
        private ContentReportRepository contentReportRepository;
        @Autowired
        private PostTranslationRepository postTranslationRepository;
        @Autowired
        private PostReactionRepository postReactionRepository;
        @Autowired
        private PostCommentRepository postCommentRepository;
        @Autowired
        private PostRepository postRepository;
        @Autowired
        private PracticeResultRepository practiceResultRepository;
        @Autowired
        private PracticeSessionRepository practiceSessionRepository;
        @Autowired
        private SavedWordRepository savedWordRepository;
        @Autowired
        private UserSettingsRepository userSettingsRepository;
        @Autowired
        private UserBlockRepository userBlockRepository;
        @Autowired
        private FollowRepository followRepository;
        @Autowired
        private RefreshTokenRepository refreshTokenRepository;

        private Profile testUser;
        private Language spanish;
        private Language japanese;

        @BeforeEach
        void setUp() {
                // Clean up all repositories
                contentReportRepository.deleteAll();
                postTranslationRepository.deleteAll();
                postReactionRepository.deleteAll();
                postCommentRepository.deleteAll();
                postRepository.deleteAll();
                practiceResultRepository.deleteAll();
                practiceSessionRepository.deleteAll();
                savedWordRepository.deleteAll();
                userLanguageRepository.deleteAll();
                userSettingsRepository.deleteAll();
                userBlockRepository.deleteAll();
                followRepository.deleteAll();
                refreshTokenRepository.deleteAll();
                profileRepository.deleteAll();
                languageRepository.deleteAll();

                // Create test user (current user)
                testUser = Profile.builder()
                                .username("testuser")
                                .email("test@example.com")
                                .passwordHash("hashed_password")
                                .displayName("Test User")
                                .locationVisibility(LocationVisibility.PUBLIC)
                                .latitude(-33.8688) // Sydney
                                .longitude(151.2093)
                                .build();
                profileRepository.save(testUser);

                // Create languages
                spanish = Language.builder()
                                .code("es")
                                .name("Spanish")
                                .flagEmoji("🇪🇸")
                                .build();
                languageRepository.save(spanish);

                japanese = Language.builder()
                                .code("ja")
                                .name("Japanese")
                                .flagEmoji("🇯🇵")
                                .build();
                languageRepository.save(japanese);
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_WithValidCoordinates_ReturnsLearners() throws Exception {
                // Create nearby user (within 10km of Sydney)
                Profile nearbyUser = Profile.builder()
                                .username("nearby")
                                .email("nearby@example.com")
                                .passwordHash("hash")
                                .displayName("Nearby User")
                                .locationVisibility(LocationVisibility.PUBLIC)
                                .latitude(-33.8700) // Very close to Sydney
                                .longitude(151.2100)
                                .build();
                profileRepository.save(nearbyUser);

                // Add language to nearby user
                UserLanguage userLang = new UserLanguage();
                userLang.setProfile(nearbyUser);
                userLang.setLanguage(spanish);
                userLang.setProficiency(ProficiencyLevel.BEGINNER);
                userLang.setLearning(true);
                userLanguageRepository.save(userLang);

                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "-33.8688")
                                .param("longitude", "151.2093")
                                .param("radius_km", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.learners").isArray())
                                .andExpect(jsonPath("$.learners.length()").value(1))
                                .andExpect(jsonPath("$.learners[0].display_name").value("Nearby User"))
                                .andExpect(jsonPath("$.learners[0].distance_km").exists())
                                .andExpect(jsonPath("$.learners[0].languages").isArray());
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_WithRadiusFilter_ReturnsOnlyWithinRadius() throws Exception {
                // Create nearby user (within 5km)
                Profile nearbyUser = Profile.builder()
                                .username("nearby")
                                .email("nearby@example.com")
                                .passwordHash("hash")
                                .displayName("Nearby User")
                                .locationVisibility(LocationVisibility.PUBLIC)
                                .latitude(-33.8700)
                                .longitude(151.2100)
                                .build();
                profileRepository.save(nearbyUser);

                // Create far user (more than 5km away)
                Profile farUser = Profile.builder()
                                .username("far")
                                .email("far@example.com")
                                .passwordHash("hash")
                                .displayName("Far User")
                                .locationVisibility(LocationVisibility.PUBLIC)
                                .latitude(-33.9200) // Further from Sydney
                                .longitude(151.2500)
                                .build();
                profileRepository.save(farUser);

                // Query with 5km radius
                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "-33.8688")
                                .param("longitude", "151.2093")
                                .param("radius_km", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.learners").isArray())
                                .andExpect(jsonPath("$.learners.length()").value(1))
                                .andExpect(jsonPath("$.learners[0].display_name").value("Nearby User"));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_WithLanguageFilter_ReturnsOnlyMatchingLanguage() throws Exception {
                // Create user learning Spanish
                Profile spanishLearner = Profile.builder()
                                .username("spanish_learner")
                                .email("spanish@example.com")
                                .passwordHash("hash")
                                .displayName("Spanish Learner")
                                .locationVisibility(LocationVisibility.PUBLIC)
                                .latitude(-33.8700)
                                .longitude(151.2100)
                                .build();
                profileRepository.save(spanishLearner);

                UserLanguage spanishUserLang = new UserLanguage();
                spanishUserLang.setProfile(spanishLearner);
                spanishUserLang.setLanguage(spanish);
                spanishUserLang.setProficiency(ProficiencyLevel.BEGINNER);
                spanishUserLang.setLearning(true);
                userLanguageRepository.save(spanishUserLang);

                // Create user learning Japanese
                Profile japaneseLearner = Profile.builder()
                                .username("japanese_learner")
                                .email("japanese@example.com")
                                .passwordHash("hash")
                                .displayName("Japanese Learner")
                                .locationVisibility(LocationVisibility.PUBLIC)
                                .latitude(-33.8700)
                                .longitude(151.2100)
                                .build();
                profileRepository.save(japaneseLearner);

                UserLanguage japaneseUserLang = new UserLanguage();
                japaneseUserLang.setProfile(japaneseLearner);
                japaneseUserLang.setLanguage(japanese);
                japaneseUserLang.setProficiency(ProficiencyLevel.BEGINNER);
                japaneseUserLang.setLearning(true);
                userLanguageRepository.save(japaneseUserLang);

                // Query with Spanish language filter
                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "-33.8688")
                                .param("longitude", "151.2093")
                                .param("radius_km", "10")
                                .param("language", "es"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.learners").isArray())
                                .andExpect(jsonPath("$.learners.length()").value(1))
                                .andExpect(jsonPath("$.learners[0].display_name").value("Spanish Learner"));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_EmptyResult_ReturnsEmptyArray() throws Exception {
                // No nearby users created

                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "-33.8688")
                                .param("longitude", "151.2093")
                                .param("radius_km", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.learners").isArray())
                                .andExpect(jsonPath("$.learners.length()").value(0));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_MissingLatitude_ReturnsBadRequest() throws Exception {
                mockMvc.perform(get("/api/learners/nearby")
                                .param("longitude", "151.2093")
                                .param("radius_km", "10"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_MissingLongitude_ReturnsBadRequest() throws Exception {
                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "-33.8688")
                                .param("radius_km", "10"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_InvalidLatitude_ReturnsBadRequest() throws Exception {
                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "100") // Invalid: > 90
                                .param("longitude", "151.2093")
                                .param("radius_km", "10"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_InvalidLongitude_ReturnsBadRequest() throws Exception {
                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "-33.8688")
                                .param("longitude", "200") // Invalid: > 180
                                .param("radius_km", "10"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_NegativeRadius_ReturnsBadRequest() throws Exception {
                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "-33.8688")
                                .param("longitude", "151.2093")
                                .param("radius_km", "-5"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void getNearbyLearners_Unauthenticated_ReturnsForbidden() throws Exception {
                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "-33.8688")
                                .param("longitude", "151.2093")
                                .param("radius_km", "10"))
                                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_ExcludesCurrentUser_Success() throws Exception {
                // Current user (testUser) has coordinates, but should not appear in results

                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "-33.8688")
                                .param("longitude", "151.2093")
                                .param("radius_km", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.learners").isArray())
                                .andExpect(jsonPath("$.learners.length()").value(0)); // Should not include self
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_ExcludesUsersWithoutLocation_Success() throws Exception {
                // Create user without location
                Profile noLocationUser = Profile.builder()
                                .username("no_location")
                                .email("nolocation@example.com")
                                .passwordHash("hash")
                                .displayName("No Location User")
                                .latitude(null)
                                .longitude(null)
                                .build();
                profileRepository.save(noLocationUser);

                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "-33.8688")
                                .param("longitude", "151.2093")
                                .param("radius_km", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.learners").isArray())
                                .andExpect(jsonPath("$.learners.length()").value(0));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getNearbyLearners_OrdersByDistance_Success() throws Exception {
                // Create two users at different distances
                Profile closerUser = Profile.builder()
                                .username("closer")
                                .email("closer@example.com")
                                .passwordHash("hash")
                                .displayName("Closer User")
                                .locationVisibility(LocationVisibility.PUBLIC)
                                .latitude(-33.8690) // Very close
                                .longitude(151.2095)
                                .build();
                profileRepository.save(closerUser);

                Profile fartherUser = Profile.builder()
                                .username("farther")
                                .email("farther@example.com")
                                .passwordHash("hash")
                                .displayName("Farther User")
                                .locationVisibility(LocationVisibility.PUBLIC)
                                .latitude(-33.8750) // A bit farther
                                .longitude(151.2150)
                                .build();
                profileRepository.save(fartherUser);

                mockMvc.perform(get("/api/learners/nearby")
                                .param("latitude", "-33.8688")
                                .param("longitude", "151.2093")
                                .param("radius_km", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.learners").isArray())
                                .andExpect(jsonPath("$.learners.length()").value(2))
                                .andExpect(jsonPath("$.learners[0].display_name").value("Closer User"))
                                .andExpect(jsonPath("$.learners[1].display_name").value("Farther User"));
        }
}
