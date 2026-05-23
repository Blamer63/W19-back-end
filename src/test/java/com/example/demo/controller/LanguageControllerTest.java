package com.example.demo.controller;

import com.example.demo.entity.Language;
import com.example.demo.repository.LanguageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class LanguageControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private LanguageRepository languageRepository;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
                languageRepository.deleteAll();
                languageRepository.saveAll(List.of(
                                language("en", "English"),
                                language("es", "Spanish"),
                                language("fr", "French"),
                                language("ja", "Japanese"),
                                language("pt", "Portuguese"),
                                language("ko", "Korean"),
                                language("vi", "Vietnamese")));
        }

        @Test
        @WithMockUser
        void getAllLanguages_Success() throws Exception {
                mockMvc.perform(get("/api/languages"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.languages").isArray());
        }

        @Test
        @WithMockUser
        void getAllLanguages_ReturnsLanguageData() throws Exception {
                mockMvc.perform(get("/api/languages"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.languages").isArray())
                                .andExpect(jsonPath("$.languages[0].code").exists())
                                .andExpect(jsonPath("$.languages[0].name").exists());
        }

        @Test
        void languageRepositoryUsesCanonicalProductLanguageCodes() {
                Set<String> codes = languageRepository.findAll().stream()
                                .map(Language::getCode)
                                .collect(Collectors.toSet());

                assertThat(codes).containsExactlyInAnyOrder("en", "es", "fr", "ja", "pt", "ko", "vi");
                assertThat(codes).doesNotContain("de", "zh");
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
