package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearnerResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("distance_km")
    private Double distanceKm;

    @JsonProperty("languages")
    private List<LanguageInfo> languages;

    @JsonProperty("learning_languages")
    private List<LanguageInfo> learningLanguages;
}
