package com.example.demo.service;

import com.example.demo.dto.BoundingBoxDTO;
import com.example.demo.dto.CreateWordRequest;
import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.dto.SavedWordResponse;
import com.example.demo.dto.ScanResponse;
import com.example.demo.dto.ScanSessionSummaryResponse;
import com.example.demo.entity.Profile;
import com.example.demo.entity.ScanDetection;
import com.example.demo.entity.ScanSession;
import com.example.demo.enums.NotificationType;
import com.example.demo.enums.SourceType;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.ScanDetectionRepository;
import com.example.demo.repository.ScanSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScanSessionService {

    private final ProfileRepository profileRepository;
    private final ScanSessionRepository scanSessionRepository;
    private final ScanDetectionRepository scanDetectionRepository;
    private final SavedWordService savedWordService;
    private final NotificationService notificationService;

    @Transactional
    public ScanResponse recordScan(String userEmail, List<DetectedObjectDTO> detectedObjects) {
        Profile user = getUser(userEmail);
        ScanSession scanSession = ScanSession.builder()
                .user(user)
                .build();

        detectedObjects.forEach(object -> scanSession.addDetection(toEntity(object)));

        ScanSession savedSession = scanSessionRepository.save(scanSession);
        if (!savedSession.getDetections().isEmpty()) {
            notificationService.createNotification(
                    user.getId(),
                    null,
                    NotificationType.SCAN_DETECTED_WORD,
                    "Scan completed",
                    "Detected " + savedSession.getDetections().size() + " word(s) in your scan.",
                    "/scan/history/" + savedSession.getId());
        }
        return toScanResponse(savedSession);
    }

    @Transactional(readOnly = true)
    public Page<ScanSessionSummaryResponse> getScanSessions(String userEmail, int page, int size) {
        Profile user = getUser(userEmail);
        Pageable pageable = PageRequest.of(
                page,
                Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return scanSessionRepository.findByUserId(user.getId(), pageable)
                .map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public ScanResponse getScanSession(String userEmail, UUID scanSessionId) {
        Profile user = getUser(userEmail);
        ScanSession scanSession = scanSessionRepository.findByIdAndUserId(scanSessionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Scan session not found"));

        return toScanResponse(scanSession);
    }

    @Transactional
    public SavedWordResponse saveDetection(String userEmail, UUID detectionId) {
        Profile user = getUser(userEmail);
        ScanDetection detection = scanDetectionRepository.findByIdAndScanSessionUserId(detectionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Scan detection not found"));

        CreateWordRequest request = CreateWordRequest.builder()
                .word(detection.getLearningWord())
                .translation(detection.getNativeWord())
                .languageCode(detection.getLanguageCode())
                .source(SourceType.SCANNER)
                .sourceId(detection.getId())
                .sourceContext("Detected in photo with " + Math.round(detection.getConfidence() * 100) + "% confidence")
                .build();

        return savedWordService.saveWord(userEmail, request);
    }

    private Profile getUser(String userEmail) {
        return profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private ScanDetection toEntity(DetectedObjectDTO object) {
        BoundingBoxDTO box = object.getBox();
        return ScanDetection.builder()
                .label(object.getLabel())
                .confidence(object.getConfidence())
                .nativeWord(object.getNativeWord())
                .learningWord(object.getLearningWord())
                .languageCode(object.getLanguageCode())
                .translationSource(object.getTranslationSource())
                .boxX(box != null ? box.getX() : null)
                .boxY(box != null ? box.getY() : null)
                .boxWidth(box != null ? box.getWidth() : null)
                .boxHeight(box != null ? box.getHeight() : null)
                .build();
    }

    private ScanResponse toScanResponse(ScanSession scanSession) {
        return ScanResponse.builder()
                .scanSessionId(scanSession.getId())
                .detectedObjects(scanSession.getDetections().stream()
                        .map(this::toDetectedObject)
                        .toList())
                .build();
    }

    private DetectedObjectDTO toDetectedObject(ScanDetection detection) {
        BoundingBoxDTO box = null;
        if (detection.getBoxX() != null
                && detection.getBoxY() != null
                && detection.getBoxWidth() != null
                && detection.getBoxHeight() != null) {
            box = BoundingBoxDTO.builder()
                    .x(detection.getBoxX())
                    .y(detection.getBoxY())
                    .width(detection.getBoxWidth())
                    .height(detection.getBoxHeight())
                    .build();
        }

        return DetectedObjectDTO.builder()
                .id(detection.getId())
                .label(detection.getLabel())
                .confidence(detection.getConfidence())
                .nativeWord(detection.getNativeWord())
                .learningWord(detection.getLearningWord())
                .languageCode(detection.getLanguageCode())
                .translationSource(detection.getTranslationSource())
                .box(box)
                .build();
    }

    private ScanSessionSummaryResponse toSummaryResponse(ScanSession scanSession) {
        return ScanSessionSummaryResponse.builder()
                .id(scanSession.getId())
                .detectedCount(scanSession.getDetections().size())
                .status(scanSession.getStatus())
                .createdAt(scanSession.getCreatedAt())
                .build();
    }
}
