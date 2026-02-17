package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.MeetupStatus;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetupService {

    private final MeetupRepository meetupRepository;
    private final MeetupAttendeeRepository meetupAttendeeRepository;
    private final ProfileRepository profileRepository;
    private final LanguageRepository languageRepository;

    @Transactional
    public MeetupResponse createMeetup(CreateMeetupRequest request, String organizerEmail) {
        Profile organizer = profileRepository.findByEmail(organizerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Language language = languageRepository.findByCode(request.getLanguageCode())
                .orElseThrow(() -> new IllegalArgumentException("Language not found"));

        Meetup meetup = Meetup.builder()
                .organizer(organizer)
                .title(request.getTitle())
                .description(request.getDescription())
                .language(language)
                .meetupDate(request.getMeetupDate())
                .location(request.getLocation())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .maxAttendees(request.getMaxAttendees())
                .status(MeetupStatus.UPCOMING)
                .build();

        meetupRepository.save(meetup);

        // Auto-add organizer as first attendee
        MeetupAttendee organizerAttendee = MeetupAttendee.builder()
                .meetup(meetup)
                .attendee(organizer)
                .joinedAt(Instant.now())
                .build();
        meetupAttendeeRepository.save(organizerAttendee);

        return mapToResponse(meetup, organizer.getId());
    }

    public Page<MeetupResponse> listMeetups(
            String languageCode,
            Double latitude,
            Double longitude,
            Double radiusKm,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size,
            UUID currentUserId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("meetupDate").ascending());
        LocalDateTime now = LocalDateTime.now();

        Page<Meetup> meetups;

        if (latitude != null && longitude != null && radiusKm != null) {
            // Geospatial search
            meetups = meetupRepository.findNearbyMeetups(
                    latitude, longitude, radiusKm, MeetupStatus.UPCOMING, now, pageable);
        } else if (languageCode != null) {
            // Filter by language
            meetups = meetupRepository.findByLanguageCodeAndStatusAndMeetupDateAfter(
                    languageCode, MeetupStatus.UPCOMING, now, pageable);
        } else {
            // All upcoming meetups
            meetups = meetupRepository.findByStatusAndMeetupDateAfter(
                    MeetupStatus.UPCOMING, now, pageable);
        }

        return meetups.map(meetup -> mapToResponse(meetup, currentUserId));
    }

    public MeetupResponse getMeetupById(UUID meetupId, UUID currentUserId) {
        Meetup meetup = meetupRepository.findById(meetupId)
                .orElseThrow(() -> new RuntimeException("Meetup not found"));

        return mapToResponse(meetup, currentUserId);
    }

    @Transactional
    public MeetupResponse updateMeetup(UUID meetupId, UpdateMeetupRequest request, UUID userId) {
        Meetup meetup = meetupRepository.findById(meetupId)
                .orElseThrow(() -> new RuntimeException("Meetup not found"));

        // Only organizer can update
        if (!meetup.getOrganizer().getId().equals(userId)) {
            throw new RuntimeException("Only the organizer can update this meetup");
        }

        // Update fields if provided
        if (request.getTitle() != null) {
            meetup.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            meetup.setDescription(request.getDescription());
        }
        if (request.getLanguageCode() != null) {
            Language language = languageRepository.findByCode(request.getLanguageCode())
                    .orElseThrow(() -> new IllegalArgumentException("Language not found"));
            meetup.setLanguage(language);
        }
        if (request.getMeetupDate() != null) {
            meetup.setMeetupDate(request.getMeetupDate());
        }
        if (request.getLocation() != null) {
            meetup.setLocation(request.getLocation());
        }
        if (request.getLatitude() != null) {
            meetup.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            meetup.setLongitude(request.getLongitude());
        }
        if (request.getMaxAttendees() != null) {
            meetup.setMaxAttendees(request.getMaxAttendees());
        }

        meetupRepository.save(meetup);
        return mapToResponse(meetup, userId);
    }

    @Transactional
    public void deleteMeetup(UUID meetupId, UUID userId) {
        Meetup meetup = meetupRepository.findById(meetupId)
                .orElseThrow(() -> new RuntimeException("Meetup not found"));

        // Only organizer can delete
        if (!meetup.getOrganizer().getId().equals(userId)) {
            throw new RuntimeException("Only the organizer can delete this meetup");
        }

        meetupRepository.delete(meetup);
    }

    @Transactional
    public void joinMeetup(UUID meetupId, UUID userId) {
        Meetup meetup = meetupRepository.findById(meetupId)
                .orElseThrow(() -> new RuntimeException("Meetup not found"));

        // Check if already joined
        if (meetupAttendeeRepository.existsByMeetupIdAndAttendeeId(meetupId, userId)) {
            throw new IllegalArgumentException("You have already joined this meetup");
        }

        // Check if meetup is in the past
        if (meetup.getMeetupDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Cannot join a past meetup");
        }

        // Check if meetup is full
        if (meetup.getMaxAttendees() != null) {
            long currentCount = meetupAttendeeRepository.countByMeetupId(meetupId);
            if (currentCount >= meetup.getMaxAttendees()) {
                throw new IllegalArgumentException("Meetup is full");
            }
        }

        Profile attendee = profileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        MeetupAttendee meetupAttendee = MeetupAttendee.builder()
                .meetup(meetup)
                .attendee(attendee)
                .joinedAt(Instant.now())
                .build();

        meetupAttendeeRepository.save(meetupAttendee);
    }

    @Transactional
    public void leaveMeetup(UUID meetupId, UUID userId) {
        Meetup meetup = meetupRepository.findById(meetupId)
                .orElseThrow(() -> new RuntimeException("Meetup not found"));

        // Prevent organizer from leaving
        if (meetup.getOrganizer().getId().equals(userId)) {
            throw new IllegalArgumentException("Organizer cannot leave the meetup. Delete it instead.");
        }

        MeetupAttendee attendee = meetupAttendeeRepository.findByMeetupIdAndAttendeeId(meetupId, userId)
                .orElseThrow(() -> new RuntimeException("You are not attending this meetup"));

        meetupAttendeeRepository.delete(attendee);
    }

    public List<MeetupAttendeeResponse> getAttendees(UUID meetupId) {
        List<MeetupAttendee> attendees = meetupAttendeeRepository.findByMeetupId(meetupId);

        return attendees.stream()
                .map(this::mapToAttendeeResponse)
                .collect(Collectors.toList());
    }

    private MeetupResponse mapToResponse(Meetup meetup, UUID currentUserId) {
        long attendeeCount = meetupAttendeeRepository.countByMeetupId(meetup.getId());
        boolean isAttending = meetupAttendeeRepository.existsByMeetupIdAndAttendeeId(
                meetup.getId(), currentUserId);
        boolean isOrganizer = meetup.getOrganizer().getId().equals(currentUserId);

        return MeetupResponse.builder()
                .id(meetup.getId())
                .organizer(MeetupResponse.MeetupOrganizerInfo.builder()
                        .id(meetup.getOrganizer().getId())
                        .displayName(meetup.getOrganizer().getDisplayName())
                        .avatarUrl(meetup.getOrganizer().getAvatarUrl())
                        .build())
                .title(meetup.getTitle())
                .description(meetup.getDescription())
                .language(LanguageInfo.builder()
                        .code(meetup.getLanguage().getCode())
                        .name(meetup.getLanguage().getName())
                        .flagEmoji(meetup.getLanguage().getFlagEmoji())
                        .build())
                .meetupDate(meetup.getMeetupDate())
                .location(meetup.getLocation())
                .latitude(meetup.getLatitude())
                .longitude(meetup.getLongitude())
                .maxAttendees(meetup.getMaxAttendees())
                .attendeeCount(attendeeCount)
                .isAttending(isAttending)
                .isOrganizer(isOrganizer)
                .status(meetup.getStatus().name())
                .createdAt(meetup.getCreatedAt())
                .build();
    }

    private MeetupAttendeeResponse mapToAttendeeResponse(MeetupAttendee attendee) {
        return MeetupAttendeeResponse.builder()
                .id(attendee.getAttendee().getId())
                .displayName(attendee.getAttendee().getDisplayName())
                .avatarUrl(attendee.getAttendee().getAvatarUrl())
                .joinedAt(attendee.getJoinedAt())
                .build();
    }
}
