package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.service.MeetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/meetups")
@RequiredArgsConstructor
public class MeetupController {

    private final MeetupService meetupService;
    private final com.example.demo.repository.ProfileRepository profileRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listMeetups(
            @RequestParam(value = "language", required = false) String languageCode,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "radius_km", required = false) Double radiusKm,
            @RequestParam(value = "start_date", required = false) LocalDateTime startDate,
            @RequestParam(value = "end_date", required = false) LocalDateTime endDate,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Authentication authentication) {

        var currentUser = profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Page<MeetupResponse> meetups = meetupService.listMeetups(
                languageCode, latitude, longitude, radiusKm, startDate, endDate,
                page, size, currentUser.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups.getContent());
        response.put("total_pages", meetups.getTotalPages());
        response.put("total_elements", meetups.getTotalElements());
        response.put("current_page", page);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<MeetupResponse> createMeetup(
            @Valid @RequestBody CreateMeetupRequest request,
            Authentication authentication) {

        MeetupResponse meetup = meetupService.createMeetup(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(meetup);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetupResponse> getMeetupById(
            @PathVariable UUID id,
            Authentication authentication) {

        var currentUser = profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        MeetupResponse meetup = meetupService.getMeetupById(id, currentUser.getId());
        return ResponseEntity.ok(meetup);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MeetupResponse> updateMeetup(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMeetupRequest request,
            Authentication authentication) {

        var currentUser = profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        MeetupResponse meetup = meetupService.updateMeetup(id, request, currentUser.getId());
        return ResponseEntity.ok(meetup);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeetup(
            @PathVariable UUID id,
            Authentication authentication) {

        var currentUser = profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        meetupService.deleteMeetup(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Void> joinMeetup(
            @PathVariable UUID id,
            Authentication authentication) {

        var currentUser = profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        meetupService.joinMeetup(id, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveMeetup(
            @PathVariable UUID id,
            Authentication authentication) {

        var currentUser = profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        meetupService.leaveMeetup(id, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/attendees")
    public ResponseEntity<Map<String, Object>> getAttendees(@PathVariable UUID id) {
        List<MeetupAttendeeResponse> attendees = meetupService.getAttendees(id);

        Map<String, Object> response = new HashMap<>();
        response.put("attendees", attendees);

        return ResponseEntity.ok(response);
    }
}
