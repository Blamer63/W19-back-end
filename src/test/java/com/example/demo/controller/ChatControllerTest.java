package com.example.demo.controller;

import com.example.demo.dto.ChatRequest;
import com.example.demo.entity.Profile;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.ProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ProfileRepository profileRepository;

        @Autowired
        private ConversationRepository conversationRepository;

        @Autowired
        private MessageRepository messageRepository;

        @Autowired
        private ObjectMapper objectMapper;

        private Profile sender;
        private Profile recipient;

        @BeforeEach
        void setUp() {
                messageRepository.deleteAll();
                conversationRepository.deleteAll();
                profileRepository.deleteAll();

                sender = Profile.builder()
                                .username("sender")
                                .email("sender@example.com")
                                .passwordHash("password")
                                .displayName("Sender")
                                .build();
                sender = profileRepository.save(sender);

                recipient = Profile.builder()
                                .username("recipient")
                                .email("recipient@example.com")
                                .passwordHash("password")
                                .displayName("Recipient")
                                .build();
                recipient = profileRepository.save(recipient);
        }

        @Test
        @WithMockUser(username = "sender")
        void shouldCreateNewConversationAndSendMessage() throws Exception {
                ChatRequest request = new ChatRequest();
                request.setRecipientId(recipient.getId());
                request.setContent("Hello REST");

                mockMvc.perform(post("/api/conversations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").value("Hello REST"))
                                .andExpect(jsonPath("$.sender.username").value("sender"));
        }

        @Test
        @WithMockUser(username = "sender")
        void shouldGetConversations() throws Exception {
                // Create a conversation first
                ChatRequest request = new ChatRequest();
                request.setRecipientId(recipient.getId());
                request.setContent("Initialize");

                mockMvc.perform(post("/api/conversations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/conversations")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content[0].lastMessagePreview").value("Initialize"));
        }

        @Test
        @WithMockUser(username = "sender")
        void shouldGetMessagesWithPagination() throws Exception {
                // Create conversation and messages
                ChatRequest request = new ChatRequest();
                request.setRecipientId(recipient.getId());
                request.setContent("Message 1");

                String response = mockMvc.perform(post("/api/conversations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andReturn().getResponse().getContentAsString();

                UUID conversationId = UUID.fromString(objectMapper.readTree(response).get("conversationId").asText());

                for (int i = 2; i <= 5; i++) {
                        request.setContent("Message " + i);
                        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk());
                }

                mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                                .param("page", "0")
                                .param("size", "2"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(2))
                                .andExpect(jsonPath("$.totalElements").value(5));
        }
}
