package com.example.demo.controller;

import com.example.demo.config.ScannerProperties;
import com.example.demo.dto.ScannerAnalyzeRequest;
import com.example.demo.dto.ScannerAnalyzeResponse;
import com.example.demo.service.scanner.CaptionServiceClient;
import com.example.demo.service.scanner.DetectedObject;
import com.example.demo.service.scanner.InMemoryTranslationCache;
import com.example.demo.service.scanner.ObjectDetectionProvider;
import com.example.demo.service.scanner.ObjectScannerService;
import com.example.demo.service.scanner.TranslationProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class ScannerControllerTest {

    @Mock
    private ObjectDetectionProvider objectDetectionProvider;

    @Mock
    private CaptionServiceClient captionServiceClient;

    @Mock
    private TranslationProvider translationProvider;

    private final InMemoryTranslationCache translationCache = new InMemoryTranslationCache();

    private final ScannerProperties scannerProperties = defaultProps();

    @Test
    void analyze_ShouldReturnTranslatedDetections() throws Exception {
        when(objectDetectionProvider.detect(anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of(
                        DetectedObject.builder().yoloLabel("cat").yoloConfidence(0.95d).cropBase64("base1").build(),
                        DetectedObject.builder().yoloLabel("dog").yoloConfidence(0.90d).cropBase64("base2").build()));
        
        when(captionServiceClient.captionAsync(anyString()))
                .thenReturn(CompletableFuture.completedFuture(CaptionServiceClient.CaptionResponse.builder()
                        .label("feline")
                        .description("a cute cat")
                        .build()));
        
        when(translationProvider.translate("feline", "vi")).thenReturn("meo");
        when(translationProvider.translate("a cute cat", "vi")).thenReturn("mot con meo de thuong");

        ScannerAnalyzeResponse response = buildService().analyze(request("vi"));

        assertEquals("OK", response.getStatus());
        assertEquals(2, response.getDetectionCount());
        // Since both mock calls return the same caption response, they will both be 'feline' and 'a cute cat'
        assertEquals("feline", response.getDetections().get(0).getLabel());
        assertEquals("meo", response.getDetections().get(0).getTranslatedLabel());
        assertEquals("mot con meo de thuong", response.getDetections().get(0).getTranslatedDescription());
    }

    @Test
    void analyze_ShouldHandleNoDetections() throws Exception {
        when(objectDetectionProvider.detect(anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of());

        ScannerAnalyzeResponse response = buildService().analyze(request("es"));

        assertEquals("NO_OBJECTS", response.getStatus());
        assertEquals(0, response.getDetectionCount());
    }

    @Test
    void analyze_ShouldFallbackWhenTranslationFails() {
        when(objectDetectionProvider.detect(anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of(DetectedObject.builder().yoloLabel("bottle").yoloConfidence(0.88d).cropBase64("base1").build()));
        
        when(captionServiceClient.captionAsync(anyString()))
                .thenReturn(CompletableFuture.completedFuture(CaptionServiceClient.CaptionResponse.builder()
                        .label("water bottle")
                        .description("a water bottle")
                        .build()));

        when(translationProvider.translate("water bottle", "de"))
                .thenThrow(new RuntimeException("translation service unavailable"));

        ScannerAnalyzeResponse response = buildService().analyze(request("de"));

        assertEquals("OK", response.getStatus());
        assertEquals("water bottle", response.getDetections().get(0).getTranslatedLabel());
        assertFalse(response.getDetections().get(0).isTranslated());
    }

    private ScannerAnalyzeRequest request(String targetLanguage) {
        ScannerAnalyzeRequest request = new ScannerAnalyzeRequest();
        request.setImageBase64("ZmFrZS1pbWFnZS1ieXRlcw==");
        request.setTargetLanguage(targetLanguage);
        return request;
    }

    private static ScannerProperties defaultProps() {
        ScannerProperties props = new ScannerProperties();
        props.setDefaultConfidenceThreshold(0.4d);
        props.setDefaultMaxResults(3);
        return props;
    }

    private ObjectScannerService buildService() {
        return new ObjectScannerService(
                objectDetectionProvider,
                captionServiceClient,
                translationProvider,
                translationCache,
                scannerProperties);
    }
}
