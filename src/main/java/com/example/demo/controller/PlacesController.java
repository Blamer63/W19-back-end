package com.example.demo.controller;

import com.example.demo.service.PlacesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
@Slf4j
public class PlacesController {

    private final PlacesService placesService;

    /**
     * GET /api/places/autocomplete?input={query}
     * Proxies to Google Places Autocomplete (New) API.
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<Map<String, Object>> autocomplete(
            @RequestParam String input) {

        log.info("Places autocomplete request for input: {}", input);
        try {
            Map<String, Object> result = placesService.autocomplete(input);
            return ResponseEntity.ok(result);
        } catch (HttpClientErrorException e) {
            log.error("Autocomplete failed: {}", e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    /**
     * GET /api/places/{placeId}
     * Proxies to Google Places Details (New) API to get coordinates.
     */
    @GetMapping("/{placeId}")
    public ResponseEntity<Map<String, Object>> getPlaceDetails(
            @PathVariable String placeId) {

        log.info("Place details request for placeId: {}", placeId);
        try {
            Map<String, Object> result = placesService.getPlaceDetails(placeId);
            return ResponseEntity.ok(result);
        } catch (HttpClientErrorException e) {
            log.error("Place details failed: {}", e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }
}
