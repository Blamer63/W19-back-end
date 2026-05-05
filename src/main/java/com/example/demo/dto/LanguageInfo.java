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
public class LanguageInfo {

    @JsonProperty("code")
    private String code;

    @JsonProperty("name")
    private String name;

    @JsonProperty("flag_emoji")
    private String flagEmoji;

    @JsonProperty("proficiency")
    private String proficiency;

    @JsonProperty("is_learning")
    private Boolean isLearning;
}
