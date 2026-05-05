package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasteryDistributionDTO {
    private Integer beginner; // 0-25%
    private Integer learning; // 26-50%
    private Integer familiar; // 51-75%
    private Integer mastered; // 76-100%
}
