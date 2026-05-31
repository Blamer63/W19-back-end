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
public class WordResultDTO {
    @JsonProperty("word_id")
    private UUID wordId;

    private String word;

    @JsonProperty("is_correct")
    private Boolean isCorrect;

    @JsonProperty("old_mastery")
    private Integer oldMastery;

    @JsonProperty("new_mastery")
    private Integer newMastery;
}
