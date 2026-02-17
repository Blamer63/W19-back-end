package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMeetupRequest {

    @NotBlank(message = "Title is required")
    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @NotBlank(message = "Language code is required")
    @JsonProperty("language_code")
    private String languageCode;

    @NotNull(message = "Meetup date is required")
    @Future(message = "Meetup date must be in the future")
    @JsonProperty("meetup_date")
    private LocalDateTime meetupDate;

    @NotBlank(message = "Location is required")
    @JsonProperty("location")
    private String location;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @Positive(message = "Max attendees must be positive")
    @JsonProperty("max_attendees")
    private Integer maxAttendees;
}
