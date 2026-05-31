package com.example.demo.service;

import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserLanguage;
import com.example.demo.enums.ScannerTranslationSource;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.service.scanner.VisionDetection;
import com.example.demo.service.scanner.VisionServiceClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ObjectDetectionService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SCAN_SIZE = 5L * 1024 * 1024;
    private static final int MAX_DETECTED_OBJECTS = 10;

    private static final String DEFAULT_LANGUAGE_CODE = "en";

    private final VisionServiceClient visionServiceClient;
    private final ProfileRepository profileRepository;
    private final ScannerVocabularyService scannerVocabularyService;

    public ObjectDetectionService(
            VisionServiceClient visionServiceClient,
            ProfileRepository profileRepository,
            ScannerVocabularyService scannerVocabularyService) {
        this.visionServiceClient = visionServiceClient;
        this.profileRepository = profileRepository;
        this.scannerVocabularyService = scannerVocabularyService;
    }

    @Value("${app.vision.supported-languages:en,es,fr,ja}")
    private String supportedTaxonomyLanguages;

    private Set<String> supportedTaxonomyLanguageCodes;

    @PostConstruct
    void parseSupportedTaxonomyLanguages() {
        supportedTaxonomyLanguageCodes = Arrays.stream(supportedTaxonomyLanguages.split(","))
                .map(code -> code.trim().toLowerCase(Locale.ROOT))
                .filter(code -> !code.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("Image must not be empty");
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType()))
            throw new IllegalArgumentException("Unsupported image type. Allowed: jpeg, png, webp");
        if (file.getSize() > MAX_SCAN_SIZE)
            throw new IllegalArgumentException("Image exceeds 5 MB limit");
    }

    @Transactional(readOnly = true)
    public List<DetectedObjectDTO> detect(MultipartFile image, String currentUserEmail) throws IOException {
        validateImage(image);
        return detect(image.getBytes(), image.getContentType(), currentUserEmail);
    }

    @Transactional(readOnly = true)
    public List<DetectedObjectDTO> detect(byte[] imageBytes, String contentType, String currentUserEmail) {
        validateImage(imageBytes, contentType);
        String languageCode = resolveLearningLanguageCode(currentUserEmail);
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
        List<VisionDetection> detections = visionServiceClient.analyze(imageBase64, languageCode);
        Map<String, ScannerVocabularyService.VocabularyMatch> vocabularyCache = new HashMap<>();

        return detections.stream()
                .filter(detection -> detection.getCanonicalLabel() != null)
                .filter(detection -> !normalizeLabel(detection.getCanonicalLabel()).isBlank())
                .collect(Collectors.toMap(
                        detection -> normalizeLabel(detection.getCanonicalLabel()),
                        Function.identity(),
                        (existing, candidate) -> existing.getConfidence() >= candidate.getConfidence()
                                ? existing
                                : candidate))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(VisionDetection::getConfidence).reversed())
                .limit(MAX_DETECTED_OBJECTS)
                .map(detection -> toDetectedObject(detection, languageCode, vocabularyCache))
                .toList();
    }

    private void validateImage(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0)
            throw new IllegalArgumentException("Image must not be empty");
        if (!ALLOWED_IMAGE_TYPES.contains(contentType))
            throw new IllegalArgumentException("Unsupported image type. Allowed: jpeg, png, webp");
        if (imageBytes.length > MAX_SCAN_SIZE)
            throw new IllegalArgumentException("Image exceeds 5 MB limit");
    }

    private String resolveLearningLanguageCode(String currentUserEmail) {
        Profile profile = profileRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return profile.getLanguages().stream()
                .filter(UserLanguage::isLearning)
                .map(UserLanguage::getLanguage)
                .filter(language -> language != null && language.getCode() != null)
                .map(language -> language.getCode().toLowerCase(Locale.ROOT))
                .findFirst()
                .orElse(DEFAULT_LANGUAGE_CODE);
    }

    private DetectedObjectDTO toDetectedObject(
            VisionDetection detection,
            String languageCode,
            Map<String, ScannerVocabularyService.VocabularyMatch> vocabularyCache) {
        String normalizedLabel = normalizeLabel(detection.getCanonicalLabel());
        ScannerVocabularyService.VocabularyMatch vocabulary = resolveVocabulary(
                detection,
                normalizedLabel,
                languageCode,
                vocabularyCache);

        return DetectedObjectDTO.builder()
                .label(normalizedLabel)
                .confidence(detection.getConfidence())
                .nativeWord(normalizedLabel)
                .learningWord(vocabulary.getLearningWord())
                .languageCode(languageCode)
                .translationSource(vocabulary.getTranslationSource())
                .box(detection.getBox())
                .build();
    }

    private ScannerVocabularyService.VocabularyMatch resolveVocabulary(
            VisionDetection detection,
            String normalizedLabel,
            String languageCode,
            Map<String, ScannerVocabularyService.VocabularyMatch> vocabularyCache) {
        if (supportedTaxonomyLanguageCodes.contains(languageCode)) {
            String learningWord = detection.getTranslatedLabel();
            if (learningWord == null || learningWord.isBlank()) {
                learningWord = normalizedLabel;
            }
            return ScannerVocabularyService.VocabularyMatch.builder()
                    .learningWord(learningWord)
                    .translationSource(ScannerTranslationSource.TAXONOMY)
                    .build();
        }

        return vocabularyCache.computeIfAbsent(
                normalizedLabel + ":" + languageCode,
                ignored -> scannerVocabularyService.resolve(normalizedLabel, languageCode));
    }

    private String normalizeLabel(String label) {
        return label.trim()
                .replace('_', ' ')
                .toLowerCase(Locale.ROOT);
    }
}
