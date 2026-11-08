package com.unihire.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeEnhancementResult {
    private String enhancedSummary;
    private List<EnhancedExperience> enhancedExperiences;
    private List<String> suggestedKeywords;
    private List<String> tailoredSkills;
    private String aiFeedback;
}
