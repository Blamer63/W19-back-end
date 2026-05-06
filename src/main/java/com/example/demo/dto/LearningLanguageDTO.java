package com.example.demo.dto;

import com.example.demo.enums.ProficiencyLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LearningLanguageDTO {
    @com.fasterxml.jackson.annotation.JsonProperty("language_code")
    private String languageCode;

    @com.fasterxml.jackson.annotation.JsonProperty("proficiency")
    private ProficiencyLevel proficiencyLevel;
}
