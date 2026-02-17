package com.example.demo.controller;

import com.example.demo.dto.MeetupResponse;
import com.example.demo.service.MeetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meetups")
@RequiredArgsConstructor
public class MeetupController {

    private final MeetupService meetupService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMeetups(
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "radius_km", defaultValue = "50") Double radiusKm) {

        List<MeetupResponse> meetups;

        if (latitude != null && longitude != null) {
            // Get nearby meetups based on location
            meetups = meetupService.getUpcomingNearbyMeetups(latitude, longitude, radiusKm);
        } else {
            // Get all upcoming meetups
            meetups = meetupService.getAllUpcomingMeetups();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups);

        return ResponseEntity.ok(response);
    }
}
