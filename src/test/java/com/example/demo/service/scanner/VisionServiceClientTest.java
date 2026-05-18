package com.example.demo.service.scanner;

import com.example.demo.exception.ObjectDetectionUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class VisionServiceClientTest {

    private static final String ENDPOINT = "http://vision.test/analyze";

    @Test
    void requestAnalysisPostsBase64ImageAndLanguageAndDeserializesDetections() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VisionServiceClient client = new VisionServiceClient(restTemplate, ENDPOINT);

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.image").value("base64-image"))
                .andExpect(jsonPath("$.language").value("es"))
                .andRespond(withSuccess("""
                        {
                          "language": "es",
                          "description": "apple",
                          "detections": [
                            {
                              "canonical_label": "apple",
                              "translated_label": "manzana",
                              "confidence": 0.08,
                              "box": {
                                "x": 0.25,
                                "y": 0.30,
                                "width": 0.40,
                                "height": 0.50
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        VisionAnalyzeResponse response = client.requestAnalysis("base64-image", "es");

        assertThat(response.getLanguage()).isEqualTo("es");
        assertThat(response.getDetections()).hasSize(1);
        VisionDetection detection = response.getDetections().get(0);
        assertThat(detection.getCanonicalLabel()).isEqualTo("apple");
        assertThat(detection.getTranslatedLabel()).isEqualTo("manzana");
        assertThat(detection.getConfidence()).isEqualTo(0.08d);
        assertThat(detection.getBox().getWidth()).isEqualTo(0.40d);
        server.verify();
    }

    @Test
    void analyzeDefaultsNullDetectionsToEmptyList() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VisionServiceClient client = new VisionServiceClient(restTemplate, ENDPOINT);

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {
                          "language": "en",
                          "description": "none"
                        }
                        """, MediaType.APPLICATION_JSON));

        List<VisionDetection> detections = client.analyze("base64-image", "en");

        assertThat(detections).isEmpty();
        server.verify();
    }

    @Test
    void requestAnalysisDefaultsBlankLanguageToEnglish() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VisionServiceClient client = new VisionServiceClient(restTemplate, ENDPOINT);

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.language").value("en"))
                .andRespond(withSuccess("""
                        {
                          "language": "en",
                          "detections": []
                        }
                        """, MediaType.APPLICATION_JSON));

        client.requestAnalysis("base64-image", " ");

        server.verify();
    }

    @Test
    void requestAnalysisThrowsUnavailableWhenServiceReturnsError() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VisionServiceClient client = new VisionServiceClient(restTemplate, ENDPOINT);

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.requestAnalysis("base64-image", "es"))
                .isInstanceOf(ObjectDetectionUnavailableException.class)
                .hasMessage("Object detection service unavailable");
        server.verify();
    }
}
