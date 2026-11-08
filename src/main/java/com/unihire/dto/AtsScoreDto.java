package com.unihire.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsScoreDto {
    private int totalScore; // 0-100
    private int keywordScore;
    private int skillsScore;
    private int experienceScore;
    private int formattingScore;
    private int roleAlignmentScore;
    private String feedback; // e.g. "Missing key skills for Java Backend: Spring Boot"
}
