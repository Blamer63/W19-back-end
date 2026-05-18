package com.example.demo.controller;

import com.example.demo.dto.BoundingBoxDTO;
import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.dto.SavedWordResponse;
import com.example.demo.dto.ScanResponse;
import com.example.demo.dto.ScanSessionSummaryResponse;
import com.example.demo.enums.ScannerTranslationSource;
import com.example.demo.enums.SourceType;
import com.example.demo.exception.ObjectDetectionUnavailableException;
import com.example.demo.service.ObjectDetectionService;
import com.example.demo.service.ScanSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockitoBean
    private ScanSessionService scanSessionService;

    @Test
    @WithMockUser(username = "test@example.com")
    void scan_ValidImage_ReturnsDetectedObjects() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "apple.jpg",
                "image/jpeg",
                "fake-image".getBytes());
        UUID scanSessionId = UUID.randomUUID();
        UUID detectionId = UUID.randomUUID();

        when(objectDetectionService.detect(any(), eq("test@example.com")))
                .thenReturn(List.of(DetectedObjectDTO.builder()
                        .label("apple")
                        .confidence(0.94)
                        .nativeWord("apple")
                        .learningWord("manzana")
                        .languageCode("es")
                        .translationSource(ScannerTranslationSource.TAXONOMY)
                        .box(BoundingBoxDTO.builder()
                                .x(0.25d)
                                .y(0.30d)
                                .width(0.40d)
                                .height(0.50d)
                                .build())
                        .build()));
        when(scanSessionService.recordScan(eq("test@example.com"), any()))
                .thenReturn(ScanResponse.builder()
                        .scanSessionId(scanSessionId)
                        .detectedObjects(List.of(DetectedObjectDTO.builder()
                                .id(detectionId)
                                .label("apple")
                                .confidence(0.94)
                                .nativeWord("apple")
                                .learningWord("manzana")
                                .languageCode("es")
                                .translationSource(ScannerTranslationSource.TAXONOMY)
                                .box(BoundingBoxDTO.builder()
                                        .x(0.25d)
                                        .y(0.30d)
                                        .width(0.40d)
                                        .height(0.50d)
                                        .build())
                                .build()))
                        .build());

        mockMvc.perform(multipart("/api/scan").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scan_session_id").value(scanSessionId.toString()))
                .andExpect(jsonPath("$.detected_objects.length()").value(1))
                .andExpect(jsonPath("$.detected_objects[0].id").value(detectionId.toString()))
                .andExpect(jsonPath("$.detected_objects[0].label").value("apple"))
                .andExpect(jsonPath("$.detected_objects[0].confidence").value(0.94))
                .andExpect(jsonPath("$.detected_objects[0].native_word").value("apple"))
                .andExpect(jsonPath("$.detected_objects[0].learning_word").value("manzana"))
                .andExpect(jsonPath("$.detected_objects[0].language_code").value("es"))
                .andExpect(jsonPath("$.detected_objects[0].translation_source").value("TAXONOMY"))
                .andExpect(jsonPath("$.detected_objects[0].box.x").value(0.25))
                .andExpect(jsonPath("$.detected_objects[0].box.y").value(0.30))
                .andExpect(jsonPath("$.detected_objects[0].box.width").value(0.40))
                .andExpect(jsonPath("$.detected_objects[0].box.height").value(0.50));

        verify(objectDetectionService).detect(any(), eq("test@example.com"));
        verify(scanSessionService).recordScan(eq("test@example.com"), any());
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
        verifyNoInteractions(scanSessionService);
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getScanSessions_ReturnsSessionSummaries() throws Exception {
        UUID scanSessionId = UUID.randomUUID();
        when(scanSessionService.getScanSessions("test@example.com", 0, 20))
                .thenReturn(new PageImpl<>(List.of(ScanSessionSummaryResponse.builder()
                        .id(scanSessionId)
                        .detectedCount(2)
                        .status(com.example.demo.enums.ScanSessionStatus.COMPLETED)
                        .build())));

        mockMvc.perform(get("/api/scan/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(scanSessionId.toString()))
                .andExpect(jsonPath("$.content[0].detected_count").value(2))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getScanSession_ReturnsDetectedObjects() throws Exception {
        UUID scanSessionId = UUID.randomUUID();
        UUID detectionId = UUID.randomUUID();
        when(scanSessionService.getScanSession("test@example.com", scanSessionId))
                .thenReturn(ScanResponse.builder()
                        .scanSessionId(scanSessionId)
                        .detectedObjects(List.of(DetectedObjectDTO.builder()
                                .id(detectionId)
                                .label("apple")
                                .confidence(0.94)
                                .nativeWord("apple")
                                .learningWord("\uC0AC\uACFC")
                                .languageCode("ko")
                                .translationSource(ScannerTranslationSource.DICTIONARY)
                                .build()))
                        .build());

        mockMvc.perform(get("/api/scan/sessions/" + scanSessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scan_session_id").value(scanSessionId.toString()))
                .andExpect(jsonPath("$.detected_objects[0].id").value(detectionId.toString()));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void saveDetection_ReturnsSavedWord() throws Exception {
        UUID detectionId = UUID.randomUUID();
        UUID wordId = UUID.randomUUID();
        when(scanSessionService.saveDetection("test@example.com", detectionId))
                .thenReturn(SavedWordResponse.builder()
                        .id(wordId)
                        .word("apple")
                        .translation("\uC0AC\uACFC")
                        .languageCode("ko")
                        .source(SourceType.SCANNER)
                        .sourceId(detectionId)
                        .build());

        mockMvc.perform(post("/api/scan/detections/" + detectionId + "/save"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(wordId.toString()))
                .andExpect(jsonPath("$.source").value("SCANNER"))
                .andExpect(jsonPath("$.source_id").value(detectionId.toString()));
    }
}
