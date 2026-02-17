package com.example.demo.service;

import com.example.demo.dto.MeetupResponse;
import com.example.demo.entity.Meetup;
import com.example.demo.repository.MeetupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetupService {

    private final MeetupRepository meetupRepository;

    public List<MeetupResponse> getUpcomingNearbyMeetups(
            Double latitude,
            Double longitude,
            Double radiusKm) {

        List<Meetup> meetups = meetupRepository.findUpcomingNearbyMeetups(
                latitude,
                longitude,
                radiusKm,
                Instant.now());

        return meetups.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<MeetupResponse> getAllUpcomingMeetups() {
        List<Meetup> meetups = meetupRepository.findByStatusOrderByMeetupDateAsc("UPCOMING");

        return meetups.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private MeetupResponse mapToResponse(Meetup meetup) {
        return MeetupResponse.builder()
                .id(meetup.getId())
                .title(meetup.getTitle())
                .description(meetup.getDescription())
                .meetupDate(meetup.getMeetupDate())
                .location(meetup.getLocation())
                .latitude(meetup.getLatitude())
                .longitude(meetup.getLongitude())
                .languageCode(meetup.getLanguageCode())
                .maxAttendees(meetup.getMaxAttendees())
                .attendeeCount(meetup.getAttendees() != null ? meetup.getAttendees().size() : 0)
                .status(meetup.getStatus())
                .organizer(MeetupResponse.OrganizerInfo.builder()
                        .id(meetup.getOrganizer().getId())
                        .displayName(meetup.getOrganizer().getDisplayName())
                        .avatarUrl(meetup.getOrganizer().getAvatarUrl())
                        .build())
                .build();
    }
}
