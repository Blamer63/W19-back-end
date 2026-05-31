package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageStatDTO {
    private String code;
    private String name;
    private String flag;

    @JsonProperty("word_count")
    private Integer wordCount;

    @JsonProperty("average_mastery")
    private Integer averageMastery;
}
