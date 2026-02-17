package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetupResponse {

    private UUID id;

    private String title;

    private String description;

    @JsonProperty("meetup_date")
    private Instant meetupDate;

    private String location;

    private Double latitude;

    private Double longitude;

    @JsonProperty("language_code")
    private String languageCode;

    @JsonProperty("max_attendees")
    private Integer maxAttendees;

    @JsonProperty("attendee_count")
    private Integer attendeeCount;

    private String status;

    @JsonProperty("organizer")
    private OrganizerInfo organizer;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizerInfo {
        private UUID id;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("avatar_url")
        private String avatarUrl;
    }
}
