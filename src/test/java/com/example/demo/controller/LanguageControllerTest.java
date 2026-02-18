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
                if (languageRepository.count() == 0) {
                        languageRepository.save(Language.builder()
                                        .code("en")
                                        .name("English")
                                        .flagEmoji("🇺🇸")
                                        .build());
                }
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
}
