package com.example.demo.entity;

import com.example.demo.enums.LocationVisibility;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacySettings {

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonProperty("location_visibility")
    private LocationVisibility locationVisibility = LocationVisibility.PUBLIC;

    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonProperty("allow_messages")
    private String allowMessages = "everyone"; // everyone, following, none
}
