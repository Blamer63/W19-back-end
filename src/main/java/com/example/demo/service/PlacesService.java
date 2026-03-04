package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class PlacesService {

    private static final String AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete";
    private static final String PLACE_DETAILS_URL = "https://places.googleapis.com/v1/places/{placeId}";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public PlacesService(RestTemplate restTemplate,
            @Value("${app.google.places-key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Proxies to Google Places Autocomplete (New) API.
     * POST https://places.googleapis.com/v1/places:autocomplete
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> autocomplete(String input) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);

        Map<String, Object> body = Map.of(
                "input", input);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    AUTOCOMPLETE_URL, request, Map.class);
            log.debug("Places autocomplete response status: {}", response.getStatusCode());
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Google Places autocomplete error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * Proxies to Google Places Details (New) API.
     * GET https://places.googleapis.com/v1/places/{placeId}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPlaceDetails(String placeId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "location,displayName,formattedAddress");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    PLACE_DETAILS_URL,
                    HttpMethod.GET,
                    request,
                    Map.class,
                    placeId);
            log.debug("Places details response status: {}", response.getStatusCode());
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Google Places details error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }
}
