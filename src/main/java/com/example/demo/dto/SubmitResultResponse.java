package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitResultResponse {
    @JsonProperty("word_id")
    private UUID wordId;

    @JsonProperty("is_correct")
    private Boolean isCorrect;

    @JsonProperty("new_mastery_level")
    private Integer newMasteryLevel;

    @JsonProperty("old_mastery")
    private Integer oldMastery;

    @JsonProperty("new_mastery")
    private Integer newMastery;

    @JsonProperty("mastery_change")
    private Integer masteryChange;
}
