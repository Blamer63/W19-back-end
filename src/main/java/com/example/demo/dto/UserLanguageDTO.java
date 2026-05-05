package com.example.demo.dto;

import com.example.demo.enums.ProficiencyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLanguageDTO {
    private String code;
    private String name;

    @com.fasterxml.jackson.annotation.JsonProperty("flag_emoji")
    private String flagEmoji;

    private ProficiencyLevel proficiency;

    @com.fasterxml.jackson.annotation.JsonProperty("is_learning")
    private boolean isLearning;
}
