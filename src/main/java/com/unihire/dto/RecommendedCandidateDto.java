package com.unihire.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecommendedCandidateDto {
    private Long candidateId;
    private String name;
    private Double matchScore; // 0-100
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private Integer experienceYears;
    private String highestDegree;
    private List<String> certifications;
    private String reason; // human-readable summary
}
