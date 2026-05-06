package com.example.demo.controller;

import com.example.demo.service.PlacesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PlacesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlacesService placesService;

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/places/autocomplete?input={query}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void autocomplete_ValidInput_ReturnsSuggestions() throws Exception {
        // Arrange – build the same shape the Google API returns
        Map<String, Object> structuredFormat = Map.of(
                "mainText", Map.of("text", "University of Wollongong Library"),
                "secondaryText", Map.of("text", "Wollongong NSW, Australia"));

        Map<String, Object> placePrediction = Map.of(
                "placeId", "ChIJtest1234",
                "text", Map.of("text", "University of Wollongong Library, Wollongong NSW"),
                "structuredFormat", structuredFormat);

        Map<String, Object> suggestion = Map.of("placePrediction", placePrediction);
        Map<String, Object> googleResponse = Map.of("suggestions", List.of(suggestion));

        when(placesService.autocomplete("UOW Library")).thenReturn(googleResponse);

        // Act & Assert
        mockMvc.perform(get("/api/places/autocomplete")
                .param("input", "UOW Library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions").isArray())
                .andExpect(jsonPath("$.suggestions.length()").value(1))
                .andExpect(jsonPath("$.suggestions[0].placePrediction.placeId").value("ChIJtest1234"))
                .andExpect(jsonPath("$.suggestions[0].placePrediction.text.text")
                        .value("University of Wollongong Library, Wollongong NSW"))
                .andExpect(jsonPath("$.suggestions[0].placePrediction.structuredFormat.mainText.text")
                        .value("University of Wollongong Library"))
                .andExpect(jsonPath("$.suggestions[0].placePrediction.structuredFormat.secondaryText.text")
                        .value("Wollongong NSW, Australia"));

        verify(placesService, times(1)).autocomplete("UOW Library");
    }

    @Test
    @WithMockUser
    void autocomplete_EmptySuggestions_ReturnsEmptyList() throws Exception {
        // Arrange – Google returns no suggestions
        Map<String, Object> googleResponse = Map.of("suggestions", List.of());
        when(placesService.autocomplete("xyznonexistent")).thenReturn(googleResponse);

        // Act & Assert
        mockMvc.perform(get("/api/places/autocomplete")
                .param("input", "xyznonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions").isArray())
                .andExpect(jsonPath("$.suggestions.length()").value(0));
    }

    @Test
    @WithMockUser
    void autocomplete_MissingInputParam_ReturnsBadRequest() throws Exception {
        // Spring will reject the request before it reaches the service
        mockMvc.perform(get("/api/places/autocomplete"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(placesService);
    }

    @Test
    @WithMockUser
    void autocomplete_GoogleApiReturnsError_PropagatesStatus() throws Exception {
        // Arrange – simulate Google returning 400
        when(placesService.autocomplete("bad input"))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        // Act & Assert
        mockMvc.perform(get("/api/places/autocomplete")
                .param("input", "bad input"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void autocomplete_GoogleApiReturnsUnauthorized_PropagatesStatus() throws Exception {
        // Arrange – simulate invalid API key
        when(placesService.autocomplete("Sydney"))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        // Act & Assert
        mockMvc.perform(get("/api/places/autocomplete")
                .param("input", "Sydney"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void autocomplete_Unauthenticated_ReturnsForbidden() throws Exception {
        // No @WithMockUser – security should block the request
        mockMvc.perform(get("/api/places/autocomplete")
                .param("input", "UOW Library"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(placesService);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/places/{placeId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getPlaceDetails_ValidPlaceId_ReturnsDetails() throws Exception {
        // Arrange
        Map<String, Object> googleResponse = Map.of(
                "location", Map.of("latitude", -34.406, "longitude", 150.878),
                "displayName", Map.of("text", "University of Wollongong Library"),
                "formattedAddress", "Northfields Ave, Wollongong NSW 2522");

        when(placesService.getPlaceDetails("ChIJtest1234")).thenReturn(googleResponse);

        // Act & Assert
        mockMvc.perform(get("/api/places/ChIJtest1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location.latitude").value(-34.406))
                .andExpect(jsonPath("$.location.longitude").value(150.878))
                .andExpect(jsonPath("$.displayName.text").value("University of Wollongong Library"))
                .andExpect(jsonPath("$.formattedAddress").value("Northfields Ave, Wollongong NSW 2522"));

        verify(placesService, times(1)).getPlaceDetails("ChIJtest1234");
    }

    @Test
    @WithMockUser
    void getPlaceDetails_PlaceIdNotFound_ReturnsNotFound() throws Exception {
        // Arrange – Google returns 404 for an unknown placeId
        when(placesService.getPlaceDetails("ChIJunknown"))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // Act & Assert
        mockMvc.perform(get("/api/places/ChIJunknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getPlaceDetails_GoogleApiReturnsUnauthorized_PropagatesStatus() throws Exception {
        // Arrange – simulate invalid API key
        when(placesService.getPlaceDetails("ChIJtest1234"))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        // Act & Assert
        mockMvc.perform(get("/api/places/ChIJtest1234"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPlaceDetails_Unauthenticated_ReturnsForbidden() throws Exception {
        // No @WithMockUser – security should block the request
        mockMvc.perform(get("/api/places/ChIJtest1234"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(placesService);
    }
}
