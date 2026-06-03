package com.example.demo.controller;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.FriendRepository;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.MeetupAttendeeRepository;
import com.example.demo.repository.MeetupRepository;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.PostCommentRepository;
import com.example.demo.repository.PostImageRepository;
import com.example.demo.repository.PostReactionRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.SavedWordRepository;
import com.example.demo.repository.UserLanguageRepository;
import com.example.demo.service.AuthService;
import com.example.demo.service.SeedImageResolverService;
import com.example.demo.service.SeedImageResolverService.SeedImageSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.starter-seed.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserLanguageControllerTest {

    private static final String SEED_KEY = "NEW_USER_STARTER_V1";
    private static final List<String> SUPPORTED_CODES = List.of("en", "es", "fr", "ja", "ko", "pt", "vi");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private UserLanguageRepository userLanguageRepository;

    @Autowired
    private SavedWordRepository savedWordRepository;

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostImageRepository postImageRepository;

    @Autowired
    private PostReactionRepository postReactionRepository;

    @Autowired
    private PostCommentRepository postCommentRepository;

    @Autowired
    private MeetupRepository meetupRepository;

    @Autowired
    private MeetupAttendeeRepository meetupAttendeeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SeedImageResolverService seedImageResolverService;

    private Profile existingLearner;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("drop table if exists user_starter_seed_runs");
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        friendRepository.deleteAll();
        postReactionRepository.deleteAll();
        postCommentRepository.deleteAll();
        postImageRepository.deleteAll();
        postRepository.deleteAll();
        meetupAttendeeRepository.deleteAll();
        meetupRepository.deleteAll();
        savedWordRepository.deleteAll();
        userLanguageRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        profileRepository.deleteAll();
        languageRepository.deleteAll();

        languageRepository.saveAll(SUPPORTED_CODES.stream()
                .map(code -> language(code, languageName(code)))
                .toList());
        when(seedImageResolverService.resolveSeedImageUrls(anyList()))
                .thenAnswer(invocation -> {
                    List<SeedImageSource> sources = invocation.getArgument(0);
                    return sources.stream().map(SeedImageSource::sourceUrl).toList();
                });

        existingLearner = profileRepository.save(Profile.builder()
                .username("existinglearner")
                .email("existinglearner@example.com")
                .passwordHash("hash")
                .displayName("Existing Learner")
                .build());
    }

    @Test
    void registrationCreatesPendingMarkerWithoutStarterData() {
        AuthResponse response = register("pending@example.com", "pendinguser");
        Profile registered = profileRepository.findById(response.getUserId()).orElseThrow();

        assertThat(markerStatus(registered.getId())).isEqualTo("PENDING");
        assertThat(savedWordRepository.findByUserId(
                registered.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent())
                .isEmpty();
        assertThat(friendRepository.findAcceptedFriendIds(registered.getId())).isEmpty();
        assertThat(conversationRepository.count()).isZero();
        assertThat(postRepository.count()).isZero();
    }

    @Test
    void firstLanguageUpdateSeedsAllStarterDataOnceForRegisteredUser() throws Exception {
        AuthResponse response = register("starter@example.com", "starteruser");
        Profile registered = profileRepository.findById(response.getUserId()).orElseThrow();

        mockMvc.perform(put("/api/users/me/languages")
                .header("Authorization", "Bearer " + response.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(allLearningLanguagesBody()))
                .andExpect(status().isOk());

        assertThat(markerStatus(registered.getId())).isEqualTo("COMPLETED");
        for (String code : SUPPORTED_CODES) {
            assertThat(savedWordRepository.countByUserIdAndLanguageCode(registered.getId(), code))
                    .as("saved words for " + code)
                    .isGreaterThanOrEqualTo(10);
        }
        List<String> japaneseWords = savedWordRepository.findByUserIdAndLanguageCode(
                        registered.getId(), "ja", org.springframework.data.domain.Pageable.unpaged())
                .getContent()
                .stream()
                .map(word -> word.getWord())
                .toList();
        assertThat(japaneseWords)
                .contains("こんにちは", "ありがとう", "市場", "左", "右", "お願いします", "駅", "水", "珈琲", "明日")
                .doesNotContain("konnichiwa", "arigato", "ichiba", "hidari", "migi", "onegaishimasu",
                        "eki", "mizu", "koohii", "ashita");
        assertThat(japaneseWords).allMatch(this::containsJapaneseScript);

        assertThat(friendRepository.findAcceptedFriendIds(registered.getId())).hasSize(21);
        assertThat(conversationRepository.count()).isEqualTo(21);
        assertThat(messageRepository.count()).isEqualTo(63);
        assertThat(postRepository.count()).isEqualTo(21);
        assertThat(postImageRepository.count()).isEqualTo(35);
        assertThat(postReactionRepository.count()).isEqualTo(42);
        assertThat(postCommentRepository.count()).isEqualTo(42);
        assertThat(meetupRepository.count()).isEqualTo(21);
        assertThat(meetupAttendeeRepository.count()).isEqualTo(63);

        MvcResult feedResult = mockMvc.perform(get("/api/posts")
                .header("Authorization", "Bearer " + response.getAccessToken())
                .param("language", "fr"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode feed = objectMapper.readTree(feedResult.getResponse().getContentAsString());
        boolean hasCarousel = false;
        for (JsonNode post : feed.path("content")) {
            if (post.path("image_urls").size() == 3) {
                hasCarousel = true;
                break;
            }
        }
        assertThat(hasCarousel).isTrue();

        long friendCount = friendRepository.count();
        long conversationCount = conversationRepository.count();
        long messageCount = messageRepository.count();
        long postCount = postRepository.count();
        long postImageCount = postImageRepository.count();
        long wordCount = savedWordRepository.count();
        long meetupCount = meetupRepository.count();
        long attendeeCount = meetupAttendeeRepository.count();

        mockMvc.perform(put("/api/users/me/languages")
                .header("Authorization", "Bearer " + response.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(allLearningLanguagesBody()))
                .andExpect(status().isOk());

        assertThat(friendRepository.count()).isEqualTo(friendCount);
        assertThat(conversationRepository.count()).isEqualTo(conversationCount);
        assertThat(messageRepository.count()).isEqualTo(messageCount);
        assertThat(postRepository.count()).isEqualTo(postCount);
        assertThat(postImageRepository.count()).isEqualTo(postImageCount);
        assertThat(savedWordRepository.count()).isEqualTo(wordCount);
        assertThat(meetupRepository.count()).isEqualTo(meetupCount);
        assertThat(meetupAttendeeRepository.count()).isEqualTo(attendeeCount);
    }

    @Test
    @WithMockUser(username = "existinglearner@example.com")
    void existingUserWithoutPendingMarkerDoesNotSeedOnLanguageUpdate() throws Exception {
        mockMvc.perform(put("/api/users/me/languages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [
                          {"code":"en","proficiency":"NATIVE","is_learning":false},
                          {"code":"es","proficiency":"BEGINNER","is_learning":true}
                        ]
                        """))
                .andExpect(status().isOk());

        assertThat(savedWordRepository.countByUserIdAndLanguageCode(existingLearner.getId(), "es")).isZero();
        assertThat(friendRepository.findAcceptedFriendIds(existingLearner.getId())).isEmpty();
        assertThat(conversationRepository.count()).isZero();
        assertThat(postRepository.count()).isZero();
        assertThat(meetupRepository.count()).isZero();
    }

    @Test
    void unauthenticatedLanguageUpdateDoesNotTriggerPendingSeed() throws Exception {
        AuthResponse response = register("locked@example.com", "lockeduser");
        Profile registered = profileRepository.findById(response.getUserId()).orElseThrow();

        mockMvc.perform(put("/api/users/me/languages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(allLearningLanguagesBody()))
                .andExpect(status().isForbidden());

        assertThat(markerStatus(registered.getId())).isEqualTo("PENDING");
        assertThat(savedWordRepository.count()).isZero();
        assertThat(conversationRepository.count()).isZero();
        assertThat(postRepository.count()).isZero();
    }

    @Test
    void pendingMarkerRemainsPendingWhenOnlyNativeLanguagesAreSaved() throws Exception {
        AuthResponse response = register("nativeonly@example.com", "nativeonly");
        Profile registered = profileRepository.findById(response.getUserId()).orElseThrow();

        mockMvc.perform(put("/api/users/me/languages")
                .header("Authorization", "Bearer " + response.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [
                          {"code":"en","proficiency":"NATIVE","is_learning":false}
                        ]
                        """))
                .andExpect(status().isOk());

        assertThat(markerStatus(registered.getId())).isEqualTo("PENDING");
        assertThat(savedWordRepository.count()).isZero();
        assertThat(conversationRepository.count()).isZero();
        assertThat(postRepository.count()).isZero();
    }

    private AuthResponse register(String email, String username) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword("password123");
        request.setUsername(username);
        request.setDisplayName(username);
        return authService.register(request);
    }

    private String markerStatus(UUID profileId) {
        return jdbcTemplate.queryForObject("""
                select status
                from user_starter_seed_runs
                where profile_id = ? and seed_key = ?
                """, String.class, profileId, SEED_KEY);
    }

    private String allLearningLanguagesBody() {
        return """
                [
                  {"code":"en","proficiency":"BEGINNER","is_learning":true},
                  {"code":"es","proficiency":"BEGINNER","is_learning":true},
                  {"code":"fr","proficiency":"BEGINNER","is_learning":true},
                  {"code":"ja","proficiency":"BEGINNER","is_learning":true},
                  {"code":"ko","proficiency":"BEGINNER","is_learning":true},
                  {"code":"pt","proficiency":"BEGINNER","is_learning":true},
                  {"code":"vi","proficiency":"BEGINNER","is_learning":true}
                ]
                """;
    }

    private boolean containsJapaneseScript(String value) {
        return value.codePoints()
                .anyMatch(codePoint -> {
                    Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
                    return script == Character.UnicodeScript.HIRAGANA
                            || script == Character.UnicodeScript.KATAKANA
                            || script == Character.UnicodeScript.HAN;
                });
    }

    private Language language(String code, String name) {
        return Language.builder()
                .code(code)
                .name(name)
                .nativeName(name)
                .flagEmoji("")
                .build();
    }

    private String languageName(String code) {
        return switch (code) {
            case "en" -> "English";
            case "es" -> "Spanish";
            case "fr" -> "French";
            case "ja" -> "Japanese";
            case "ko" -> "Korean";
            case "pt" -> "Portuguese";
            case "vi" -> "Vietnamese";
            default -> code;
        };
    }
}
