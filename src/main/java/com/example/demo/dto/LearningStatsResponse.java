package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningStatsResponse {
    @JsonProperty("total_words")
    private Integer totalWords;

    @JsonProperty("average_mastery")
    private Integer averageMastery;

    private List<LanguageStatDTO> languages;

    @JsonProperty("mastery_distribution")
    private MasteryDistributionDTO masteryDistribution;
}
