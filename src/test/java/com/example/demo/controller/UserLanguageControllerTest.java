package com.example.demo.controller;

import com.example.demo.entity.Language;
import com.example.demo.entity.Profile;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.FriendRepository;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.MeetupRepository;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.SavedWordRepository;
import com.example.demo.repository.UserLanguageRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserLanguageControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    private MeetupRepository meetupRepository;

    private Profile learner;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        friendRepository.deleteAll();
        postRepository.deleteAll();
        meetupRepository.deleteAll();
        savedWordRepository.deleteAll();
        userLanguageRepository.deleteAll();
        profileRepository.deleteAll();
        languageRepository.deleteAll();

        languageRepository.saveAll(List.of(
                language("en", "English"),
                language("es", "Spanish"),
                language("ja", "Japanese"),
                language("fr", "French")));

        learner = profileRepository.save(Profile.builder()
                .username("newlearner")
                .email("newlearner@example.com")
                .passwordHash("hash")
                .displayName("New Learner")
                .build());
    }

    @Test
    @WithMockUser(username = "newlearner@example.com")
    void updateUserLanguagesSeedsSpanishDemoLearningData() throws Exception {
        mockMvc.perform(put("/api/users/me/languages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [
                          {"code":"en","proficiency":"NATIVE","is_learning":false},
                          {"code":"es","proficiency":"BEGINNER","is_learning":true}
                        ]
                        """))
                .andExpect(status().isOk());

        assertThat(savedWordRepository.countByUserIdAndLanguageCode(learner.getId(), "es")).isEqualTo(5);
        assertThat(friendRepository.findAcceptedFriendIds(learner.getId())).hasSize(3);
        assertThat(conversationRepository.count()).isEqualTo(3);
        assertThat(messageRepository.count()).isEqualTo(6);
        assertThat(postRepository.count()).isEqualTo(3);
        assertThat(meetupRepository.count()).isEqualTo(3);

        long postCount = postRepository.count();
        long wordCount = savedWordRepository.countByUserIdAndLanguageCode(learner.getId(), "es");

        mockMvc.perform(put("/api/users/me/languages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [
                          {"code":"en","proficiency":"NATIVE","is_learning":false},
                          {"code":"es","proficiency":"BEGINNER","is_learning":true}
                        ]
                        """))
                .andExpect(status().isOk());

        assertThat(postRepository.count()).isEqualTo(postCount);
        assertThat(savedWordRepository.countByUserIdAndLanguageCode(learner.getId(), "es")).isEqualTo(wordCount);
    }

    @Test
    @WithMockUser(username = "newlearner@example.com")
    void updateUserLanguagesSeedsJapaneseDemoLearningData() throws Exception {
        mockMvc.perform(put("/api/users/me/languages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [
                          {"code":"en","proficiency":"NATIVE","is_learning":false},
                          {"code":"ja","proficiency":"BEGINNER","is_learning":true}
                        ]
                        """))
                .andExpect(status().isOk());

        assertThat(savedWordRepository.countByUserIdAndLanguageCode(learner.getId(), "ja")).isEqualTo(5);
        assertThat(friendRepository.findAcceptedFriendIds(learner.getId())).hasSize(3);
        assertThat(postRepository.count()).isEqualTo(3);
        assertThat(meetupRepository.count()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "newlearner@example.com")
    void updateUserLanguagesDoesNotSeedDemoDataForOtherLanguages() throws Exception {
        mockMvc.perform(put("/api/users/me/languages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [
                          {"code":"en","proficiency":"NATIVE","is_learning":false},
                          {"code":"fr","proficiency":"BEGINNER","is_learning":true}
                        ]
                        """))
                .andExpect(status().isOk());

        assertThat(savedWordRepository.countByUserIdAndLanguageCode(learner.getId(), "fr")).isZero();
        assertThat(friendRepository.findAcceptedFriendIds(learner.getId())).isEmpty();
        assertThat(postRepository.count()).isZero();
        assertThat(meetupRepository.count()).isZero();
    }

    private Language language(String code, String name) {
        return Language.builder()
                .code(code)
                .name(name)
                .nativeName(name)
                .flagEmoji("")
                .build();
    }
}
