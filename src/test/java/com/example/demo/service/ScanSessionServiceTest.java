package com.example.demo.service;

import com.example.demo.dto.BoundingBoxDTO;
import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.dto.SavedWordResponse;
import com.example.demo.dto.ScanResponse;
import com.example.demo.entity.Profile;
import com.example.demo.entity.ScanDetection;
import com.example.demo.entity.ScanSession;
import com.example.demo.enums.ScannerTranslationSource;
import com.example.demo.enums.SourceType;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.ScanDetectionRepository;
import com.example.demo.repository.ScanSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanSessionServiceTest {

    @Mock private ProfileRepository profileRepository;
    @Mock private ScanSessionRepository scanSessionRepository;
    @Mock private ScanDetectionRepository scanDetectionRepository;
    @Mock private SavedWordService savedWordService;

    private ScanSessionService scanSessionService;
    private Profile user;

    @BeforeEach
    void setUp() {
        scanSessionService = new ScanSessionService(
                profileRepository,
                scanSessionRepository,
                scanDetectionRepository,
                savedWordService);
        user = Profile.builder()
                .email("test@example.com")
                .build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void recordScanPersistsSessionAndReturnsDetectionIds() {
        UUID scanSessionId = UUID.randomUUID();
        UUID detectionId = UUID.randomUUID();
        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(scanSessionRepository.save(any(ScanSession.class))).thenAnswer(invocation -> {
            ScanSession session = invocation.getArgument(0);
            session.setId(scanSessionId);
            session.getDetections().get(0).setId(detectionId);
            return session;
        });

        ScanResponse response = scanSessionService.recordScan("test@example.com", List.of(DetectedObjectDTO.builder()
                .label("apple")
                .confidence(0.94d)
                .nativeWord("apple")
                .learningWord("\uC0AC\uACFC")
                .languageCode("ko")
                .translationSource(ScannerTranslationSource.DICTIONARY)
                .box(BoundingBoxDTO.builder()
                        .x(0.25d)
                        .y(0.30d)
                        .width(0.40d)
                        .height(0.50d)
                        .build())
                .build()));

        assertThat(response.getScanSessionId()).isEqualTo(scanSessionId);
        assertThat(response.getDetectedObjects()).hasSize(1);
        assertThat(response.getDetectedObjects().get(0).getId()).isEqualTo(detectionId);
        assertThat(response.getDetectedObjects().get(0).getBox().getX()).isEqualTo(0.25d);

        ArgumentCaptor<ScanSession> captor = ArgumentCaptor.forClass(ScanSession.class);
        verify(scanSessionRepository).save(captor.capture());
        ScanDetection savedDetection = captor.getValue().getDetections().get(0);
        assertThat(savedDetection.getScanSession()).isEqualTo(captor.getValue());
        assertThat(savedDetection.getLabel()).isEqualTo("apple");
        assertThat(savedDetection.getBoxWidth()).isEqualTo(0.40d);
    }

    @Test
    void getScanSessionsReturnsSummaries() {
        ScanSession session = ScanSession.builder()
                .user(user)
                .build();
        session.setId(UUID.randomUUID());
        session.addDetection(ScanDetection.builder()
                .label("apple")
                .confidence(0.94d)
                .nativeWord("apple")
                .learningWord("\uC0AC\uACFC")
                .languageCode("ko")
                .build());

        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(scanSessionRepository.findByUserId(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(session)));

        Page<?> response = scanSessionService.getScanSessions("test@example.com", 0, 20);

        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void saveDetectionCreatesScannerSavedWordFromDetection() {
        UUID detectionId = UUID.randomUUID();
        ScanSession session = ScanSession.builder()
                .user(user)
                .build();
        ScanDetection detection = ScanDetection.builder()
                .scanSession(session)
                .label("apple")
                .confidence(0.94d)
                .nativeWord("apple")
                .learningWord("\uC0AC\uACFC")
                .languageCode("ko")
                .build();
        detection.setId(detectionId);

        when(profileRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(scanDetectionRepository.findByIdAndScanSessionUserId(detectionId, user.getId()))
                .thenReturn(Optional.of(detection));
        when(savedWordService.saveWord(any(), any()))
                .thenReturn(SavedWordResponse.builder()
                        .word("apple")
                        .translation("\uC0AC\uACFC")
                        .languageCode("ko")
                        .source(SourceType.SCANNER)
                        .sourceId(detectionId)
                        .build());

        SavedWordResponse response = scanSessionService.saveDetection("test@example.com", detectionId);

        assertThat(response.getSource()).isEqualTo(SourceType.SCANNER);
        assertThat(response.getSourceId()).isEqualTo(detectionId);
        verify(savedWordService).saveWord(any(), org.mockito.ArgumentMatchers.argThat(request ->
                request.getWord().equals("apple")
                        && request.getTranslation().equals("\uC0AC\uACFC")
                        && request.getLanguageCode().equals("ko")
                        && request.getSource() == SourceType.SCANNER
                        && request.getSourceId().equals(detectionId)
                        && request.getSourceContext().equals("Detected in photo with 94% confidence")));
    }
}
