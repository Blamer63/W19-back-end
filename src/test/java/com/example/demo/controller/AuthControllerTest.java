package com.example.demo.controller;

import com.example.demo.dto.RegisterRequest;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.RefreshTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ProfileRepository profileRepository;

        @Autowired
        private RefreshTokenRepository refreshTokenRepository;

        @Autowired
        private ObjectMapper objectMapper;

        @BeforeEach
        void tearDown() {
                refreshTokenRepository.deleteAll();
                profileRepository.deleteAll();
        }

        @Test
        void shouldRegisterUserSuccessfully() throws Exception {
                RegisterRequest request = new RegisterRequest();
                request.setEmail("test@example.com");
                request.setPassword("password123");
                request.setUsername("testuser");
                request.setDisplayName("Test User");

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.user_id").exists())
                                .andExpect(jsonPath("$.access_token").exists());
        }

        @Test
        void shouldRegisterUserWithoutUsername() throws Exception {
                RegisterRequest request = new RegisterRequest();
                request.setEmail("register_no_username@example.com");
                request.setPassword("password123");
                request.setDisplayName("No Username User");

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.user_id").exists());

                // Verify username was generated from email prefix
                com.example.demo.entity.Profile profile = profileRepository
                                .findByEmail("register_no_username@example.com")
                                .orElseThrow();
                org.junit.jupiter.api.Assertions.assertEquals("register_no_username", profile.getUsername());
        }

        @Test
        void shouldFailRegistrationWithDuplicateEmail() throws Exception {
                // Create existing user
                RegisterRequest request1 = new RegisterRequest();
                request1.setEmail("duplicate@example.com");
                request1.setPassword("password123");
                request1.setDisplayName("User 1");

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request1)))
                                .andExpect(status().isCreated());

                // Try to register again with same email
                RegisterRequest request2 = new RegisterRequest();
                request2.setEmail("duplicate@example.com");
                request2.setPassword("password123");
                request2.setDisplayName("User 2");

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request2)))
                                .andExpect(status().isBadRequest());
        }
}
