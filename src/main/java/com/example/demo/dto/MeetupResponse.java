package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetupResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("organizer")
    private MeetupOrganizerInfo organizer;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("language")
    private LanguageInfo language;

    @JsonProperty("meetup_date")
    private LocalDateTime meetupDate;

    @JsonProperty("location")
    private String location;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("max_attendees")
    private Integer maxAttendees;

    @JsonProperty("attendee_count")
    private Long attendeeCount;

    @JsonProperty("is_attending")
    private Boolean isAttending;

    @JsonProperty("is_organizer")
    private Boolean isOrganizer;

    @JsonProperty("status")
    private String status;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MeetupOrganizerInfo {
        @JsonProperty("id")
        private UUID id;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("avatar_url")
        private String avatarUrl;
    }
}
