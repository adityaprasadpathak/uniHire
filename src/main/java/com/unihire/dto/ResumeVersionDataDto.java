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
public class ResumeVersionDataDto {
    private String candidateName;
    private String email;
    private String phone;
    private String location;
    private String professionalSummary;
    private List<String> skills;
    private List<ExperienceSection> experiences;
    private List<EducationSection> educations;
    private List<String> certifications;
    private List<String> keywords;
    private String targetIndustry;
    private String targetCountry;
    private String targetRole;
    private String resumeTemplate;
    private String resumeStyle;
    private int atsScore;
    private String aiFeedback;
}
