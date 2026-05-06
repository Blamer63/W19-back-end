package com.example.demo.controller;

import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.exception.ObjectDetectionUnavailableException;
import com.example.demo.service.ObjectDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObjectDetectionService objectDetectionService;

    @Test
    @WithMockUser(username = "test@example.com")
    void scan_ValidImage_ReturnsDetectedObjects() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "apple.jpg",
                "image/jpeg",
                "fake-image".getBytes());

        when(objectDetectionService.detect(any(), eq("test@example.com")))
                .thenReturn(List.of(DetectedObjectDTO.builder()
                        .label("apple")
                        .confidence(0.94)
                        .nativeWord("apple")
                        .learningWord("\uC0AC\uACFC")
                        .languageCode("ko")
                        .build()));

        mockMvc.perform(multipart("/api/scan").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detected_objects.length()").value(1))
                .andExpect(jsonPath("$.detected_objects[0].label").value("apple"))
                .andExpect(jsonPath("$.detected_objects[0].confidence").value(0.94))
                .andExpect(jsonPath("$.detected_objects[0].native_word").value("apple"))
                .andExpect(jsonPath("$.detected_objects[0].learning_word").value("\uC0AC\uACFC"))
                .andExpect(jsonPath("$.detected_objects[0].language_code").value("ko"));

        verify(objectDetectionService).detect(any(), eq("test@example.com"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void scan_InvalidImage_ReturnsBadRequest() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "notes.txt",
                "text/plain",
                "not-an-image".getBytes());

        when(objectDetectionService.detect(any(), eq("test@example.com")))
                .thenThrow(new IllegalArgumentException("Unsupported image type. Allowed: jpeg, png, webp"));

        mockMvc.perform(multipart("/api/scan").file(image))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported image type. Allowed: jpeg, png, webp"));

        verify(objectDetectionService).detect(any(), eq("test@example.com"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void scan_ObjectDetectionUnavailable_ReturnsServiceUnavailable() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "apple.jpg",
                "image/jpeg",
                "fake-image".getBytes());

        when(objectDetectionService.detect(any(), eq("test@example.com")))
                .thenThrow(new ObjectDetectionUnavailableException("Object detection service unavailable"));

        mockMvc.perform(multipart("/api/scan").file(image))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Object detection service unavailable"));
    }

    @Test
    void scan_Unauthenticated_ReturnsForbidden() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "apple.jpg",
                "image/jpeg",
                "fake-image".getBytes());

        mockMvc.perform(multipart("/api/scan").file(image))
                .andExpect(status().isForbidden());

        verifyNoInteractions(objectDetectionService);
    }
}
