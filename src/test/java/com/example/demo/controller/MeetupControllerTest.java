package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.enums.MeetupStatus;
import com.example.demo.enums.ProficiencyLevel;
import com.example.demo.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class MeetupControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private MeetupRepository meetupRepository;

        @Autowired
        private MeetupAttendeeRepository meetupAttendeeRepository;

        @Autowired
        private ProfileRepository profileRepository;

        @Autowired
        private LanguageRepository languageRepository;

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
        private UserLanguageRepository userLanguageRepository;
        @Autowired
        private UserSettingsRepository userSettingsRepository;
        @Autowired
        private UserBlockRepository userBlockRepository;
        @Autowired
        private FollowRepository followRepository;
        @Autowired
        private RefreshTokenRepository refreshTokenRepository;

        private Profile testUser;
        private Profile otherUser;
        private Language spanish;
        private Language japanese;

        @BeforeEach
        void setUp() {
                // Clean up all repositories
                meetupAttendeeRepository.deleteAll();
                meetupRepository.deleteAll();
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

                // Create test users
                testUser = Profile.builder()
                                .username("testuser")
                                .email("test@example.com")
                                .passwordHash("hashed_password")
                                .displayName("Test User")
                                .latitude(-33.8688)
                                .longitude(151.2093)
                                .build();
                profileRepository.save(testUser);

                otherUser = Profile.builder()
                                .username("otheruser")
                                .email("other@example.com")
                                .passwordHash("hashed_password")
                                .displayName("Other User")
                                .build();
                profileRepository.save(otherUser);

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
        void createMeetup_ValidRequest_Success() throws Exception {
                Map<String, Object> request = new HashMap<>();
                request.put("title", "Spanish Conversation");
                request.put("description", "Practice Spanish");
                request.put("language_code", "es");
                request.put("meetup_date", LocalDateTime.now().plusDays(7).toString());
                request.put("location", "Central Park");
                request.put("latitude", -33.8700);
                request.put("longitude", 151.2100);
                request.put("max_attendees", 10);

                mockMvc.perform(post("/api/meetups")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.title").value("Spanish Conversation"))
                                .andExpect(jsonPath("$.organizer.display_name").value("Test User"))
                                .andExpect(jsonPath("$.attendee_count").value(1)) // Organizer auto-joins
                                .andExpect(jsonPath("$.is_organizer").value(true))
                                .andExpect(jsonPath("$.is_attending").value(true));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getMeetupById_Exists_ReturnsDetails() throws Exception {
                Meetup meetup = createTestMeetup(testUser, spanish);

                mockMvc.perform(get("/api/meetups/" + meetup.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(meetup.getId().toString()))
                                .andExpect(jsonPath("$.title").value(meetup.getTitle()))
                                .andExpect(jsonPath("$.is_organizer").value(true));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void listMeetups_WithPagination_Success() throws Exception {
                createTestMeetup(testUser, spanish);
                createTestMeetup(testUser, japanese);

                mockMvc.perform(get("/api/meetups")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.meetups").isArray())
                                .andExpect(jsonPath("$.meetups.length()").value(2))
                                .andExpect(jsonPath("$.total_pages").exists())
                                .andExpect(jsonPath("$.current_page").value(0));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void updateMeetup_AsOrganizer_Success() throws Exception {
                Meetup meetup = createTestMeetup(testUser, spanish);

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("title", "Updated Title");
                updateRequest.put("description", "Updated Description");

                mockMvc.perform(put("/api/meetups/" + meetup.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("Updated Title"))
                                .andExpect(jsonPath("$.description").value("Updated Description"));
        }

        @Test
        @WithMockUser(username = "other@example.com")
        void updateMeetup_AsNonOrganizer_ReturnsForbidden() throws Exception {
                Meetup meetup = createTestMeetup(testUser, spanish);

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("title", "Hacked Title");

                mockMvc.perform(put("/api/meetups/" + meetup.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest()); // Not organizer
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void deleteMeetup_AsOrganizer_Success() throws Exception {
                Meetup meetup = createTestMeetup(testUser, spanish);

                mockMvc.perform(delete("/api/meetups/" + meetup.getId()))
                                .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(username = "other@example.com")
        void deleteMeetup_AsNonOrganizer_ReturnsForbidden() throws Exception {
                Meetup meetup = createTestMeetup(testUser, spanish);

                mockMvc.perform(delete("/api/meetups/" + meetup.getId()))
                                .andExpect(status().isBadRequest()); // Not organizer
        }

        @Test
        @WithMockUser(username = "other@example.com")
        void joinMeetup_ValidRequest_Success() throws Exception {
                Meetup meetup = createTestMeetup(testUser, spanish);

                mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/join"))
                                .andExpect(status().isOk());

                // Verify attendee count increased
                mockMvc.perform(get("/api/meetups/" + meetup.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.attendee_count").value(2));
        }

        @Test
        @WithMockUser(username = "other@example.com")
        void joinMeetup_AlreadyJoined_ReturnsBadRequest() throws Exception {
                Meetup meetup = createTestMeetup(testUser, spanish);

                // Join first time
                mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/join"))
                                .andExpect(status().isOk());

                // Try to join again
                mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/join"))
                                .andExpect(status().isBadRequest()); // Already joined
        }

        @Test
        @WithMockUser(username = "other@example.com")
        void joinMeetup_MeetupFull_ReturnsBadRequest() throws Exception {
                Meetup meetup = Meetup.builder()
                                .organizer(testUser)
                                .title("Small Meetup")
                                .language(spanish)
                                .meetupDate(LocalDateTime.now().plusDays(1))
                                .location("Cafe")
                                .maxAttendees(1) // Only organizer can join
                                .status(MeetupStatus.UPCOMING)
                                .build();
                meetupRepository.save(meetup);

                // Auto-add organizer
                MeetupAttendee organizerAttendee = MeetupAttendee.builder()
                                .meetup(meetup)
                                .attendee(testUser)
                                .build();
                meetupAttendeeRepository.save(organizerAttendee);

                // Try to join when full
                mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/join"))
                                .andExpect(status().isBadRequest()); // Meetup full
        }

        @Test
        @WithMockUser(username = "other@example.com")
        void joinMeetup_PastMeetup_ReturnsBadRequest() throws Exception {
                Meetup pastMeetup = Meetup.builder()
                                .organizer(testUser)
                                .title("Past Meetup")
                                .language(spanish)
                                .meetupDate(LocalDateTime.now().minusDays(1)) // In the past
                                .location("Cafe")
                                .status(MeetupStatus.UPCOMING)
                                .build();
                meetupRepository.save(pastMeetup);

                mockMvc.perform(post("/api/meetups/" + pastMeetup.getId() + "/join"))
                                .andExpect(status().isBadRequest()); // Past meetup
        }

        @Test
        @WithMockUser(username = "other@example.com")
        void leaveMeetup_AsAttendee_Success() throws Exception {
                Meetup meetup = createTestMeetup(testUser, spanish);

                // Join first
                mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/join"))
                                .andExpect(status().isOk());

                // Then leave
                mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/leave"))
                                .andExpect(status().isOk());

                // Verify attendee count decreased
                mockMvc.perform(get("/api/meetups/" + meetup.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.attendee_count").value(1)); // Only organizer
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void leaveMeetup_AsOrganizer_ReturnsForbidden() throws Exception {
                Meetup meetup = createTestMeetup(testUser, spanish);

                mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/leave"))
                                .andExpect(status().isBadRequest()); // Organizer cannot leave
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void listMeetups_FilterByLanguage_ReturnsMatching() throws Exception {
                createTestMeetup(testUser, spanish);
                createTestMeetup(testUser, japanese);

                mockMvc.perform(get("/api/meetups")
                                .param("language", "es"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.meetups").isArray())
                                .andExpect(jsonPath("$.meetups.length()").value(1))
                                .andExpect(jsonPath("$.meetups[0].language.code").value("es"));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void listMeetups_FilterByLocation_ReturnsNearby() throws Exception {
                // Create nearby meetup
                Meetup nearby = Meetup.builder()
                                .organizer(testUser)
                                .title("Nearby Meetup")
                                .language(spanish)
                                .meetupDate(LocalDateTime.now().plusDays(1))
                                .location("Sydney")
                                .latitude(-33.8700)
                                .longitude(151.2100)
                                .status(MeetupStatus.UPCOMING)
                                .build();
                meetupRepository.save(nearby);

                // Create far meetup
                Meetup far = Meetup.builder()
                                .organizer(testUser)
                                .title("Far Meetup")
                                .language(spanish)
                                .meetupDate(LocalDateTime.now().plusDays(1))
                                .location("Melbourne")
                                .latitude(-37.8136)
                                .longitude(144.9631)
                                .status(MeetupStatus.UPCOMING)
                                .build();
                meetupRepository.save(far);

                mockMvc.perform(get("/api/meetups")
                                .param("latitude", "-33.8688")
                                .param("longitude", "151.2093")
                                .param("radius_km", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.meetups").isArray())
                                .andExpect(jsonPath("$.meetups.length()").value(1))
                                .andExpect(jsonPath("$.meetups[0].title").value("Nearby Meetup"));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void getAttendees_ReturnsList_Success() throws Exception {
                Meetup meetup = createTestMeetup(testUser, spanish);

                // Add another attendee
                MeetupAttendee attendee = MeetupAttendee.builder()
                                .meetup(meetup)
                                .attendee(otherUser)
                                .build();
                meetupAttendeeRepository.save(attendee);

                mockMvc.perform(get("/api/meetups/" + meetup.getId() + "/attendees"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.attendees").isArray())
                                .andExpect(jsonPath("$.attendees.length()").value(2));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void createMeetup_MissingRequiredFields_ReturnsBadRequest() throws Exception {
                Map<String, Object> request = new HashMap<>();
                request.put("title", "Test");
                // Missing meetup_date, location, language_code

                mockMvc.perform(post("/api/meetups")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void createMeetup_Unauthenticated_ReturnsForbidden() throws Exception {
                Map<String, Object> request = new HashMap<>();
                request.put("title", "Test Meetup");

                mockMvc.perform(post("/api/meetups")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden());
        }

        // Helper method
        private Meetup createTestMeetup(Profile organizer, Language language) {
                Meetup meetup = Meetup.builder()
                                .organizer(organizer)
                                .title("Test Meetup")
                                .description("Test Description")
                                .language(language)
                                .meetupDate(LocalDateTime.now().plusDays(7))
                                .location("Test Location")
                                .latitude(-33.8700)
                                .longitude(151.2100)
                                .maxAttendees(20)
                                .status(MeetupStatus.UPCOMING)
                                .build();
                meetupRepository.save(meetup);

                // Auto-add organizer as attendee
                MeetupAttendee organizerAttendee = MeetupAttendee.builder()
                                .meetup(meetup)
                                .attendee(organizer)
                                .build();
                meetupAttendeeRepository.save(organizerAttendee);

                return meetup;
        }
}
