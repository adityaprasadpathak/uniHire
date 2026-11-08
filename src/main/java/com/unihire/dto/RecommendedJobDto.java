package com.unihire.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecommendedJobDto {
    private Long jobId;
    private String title;
    private String companyName;
    private String location;
    private String employmentType;
    private String salary;
    private Double matchScore; // 0-100
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private String reason; // human-readable summary
}
