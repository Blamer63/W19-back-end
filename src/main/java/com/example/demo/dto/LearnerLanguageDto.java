package com.example.demo.dto;

import com.example.demo.enums.ProficiencyLevel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LearnerLanguageDto {
    private String code;
    private String name;
    private String flagEmoji;
    private ProficiencyLevel proficiency;
    private boolean isLearning;
}
