package com.example.demo.controller;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
        @WithMockUser(username = "sender@example.com")
        void shouldCreateNewConversationAndSendMessage() throws Exception {
                mockMvc.perform(multipart("/api/conversations")
                                .param("recipientId", recipient.getId().toString())
                                .param("content", "Hello REST"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").value("Hello REST"))
                                .andExpect(jsonPath("$.sender.username").value("sender"));
        }

        @Test
        @WithMockUser(username = "sender@example.com")
        void shouldGetConversations() throws Exception {
                mockMvc.perform(multipart("/api/conversations")
                                .param("recipientId", recipient.getId().toString())
                                .param("content", "Initialize"))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/conversations")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content[0].lastMessagePreview").value("Initialize"));
        }

        @Test
        @WithMockUser(username = "sender@example.com")
        void shouldGetMessagesWithPagination() throws Exception {
                String response = mockMvc.perform(multipart("/api/conversations")
                                .param("recipientId", recipient.getId().toString())
                                .param("content", "Message 1"))
                                .andReturn().getResponse().getContentAsString();

                UUID conversationId = UUID.fromString(objectMapper.readTree(response).get("conversationId").asText());

                for (int i = 2; i <= 5; i++) {
                        mockMvc.perform(multipart("/api/conversations/{id}/messages", conversationId)
                                        .param("content", "Message " + i))
                                        .andExpect(status().isOk());
                }

                mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                                .param("page", "0")
                                .param("size", "2"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(2))
                                .andExpect(jsonPath("$.totalElements").value(5));
        }

        @Test
        @WithMockUser(username = "sender@example.com")
        void shouldCreateGroupConversation() throws Exception {
                Profile member2 = Profile.builder()
                                .username("member2")
                                .email("member2@example.com")
                                .passwordHash("password")
                                .displayName("Member Two")
                                .build();
                member2 = profileRepository.save(member2);

                String body = """
                                {
                                  "groupName": "Study Group",
                                  "participantIds": ["%s", "%s"]
                                }
                                """.formatted(recipient.getId(), member2.getId());

                mockMvc.perform(post("/api/conversations/group")
                                .contentType("application/json")
                                .content(body))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isGroup").value(true))
                                .andExpect(jsonPath("$.groupName").value("Study Group"));
        }

        @Test
        @WithMockUser(username = "sender@example.com")
        void shouldRejectGroupConversationWithBlankName() throws Exception {
                String body = """
                                {
                                  "groupName": "   ",
                                  "participantIds": ["%s", "%s"]
                                }
                                """.formatted(recipient.getId(), UUID.randomUUID());

                mockMvc.perform(post("/api/conversations/group")
                                .contentType("application/json")
                                .content(body))
                                .andExpect(status().isBadRequest());
        }
}
