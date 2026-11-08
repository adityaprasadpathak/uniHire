package com.unihire.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobRequestDto {
    private String title;
    private String description;
    private String experience;
    private Integer minExperience;
    private Integer maxExperience;
    private String salary;
    private String skills;
    private String preferredSkills;
    private String location;
    private String department;
    private String employmentType;
    private Integer numberOfVacancies;
    private String benefits;
    private LocalDateTime expiryDate;

    // Additional fields
    private String jobLevel;
    private String workMode;
    private Integer openings;
    private String education;
    private String certifications;
    private String languages;
    private String responsibilities;
    private String qualifications;
    private String workingDays;
    private String shiftTiming;
    private String travelRequirement;
    private String noticePeriod;
    private String employmentCategory;
    private String industry;
    private String incentives;
    private String hiringManager;
    private String recruiter;
    private String interviewProcess;
    private String screeningQuestions;
}
