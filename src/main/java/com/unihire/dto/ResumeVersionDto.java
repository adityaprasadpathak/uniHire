package com.unihire.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ResumeVersionDto {
    private Long id;
    private String versionName;
    private String versionDataJson;
    private String targetIndustry;
    private String targetCountry;
    private LocalDateTime updatedAt;

    // New fields
    private String generationStatus;
    private String targetRole;
    private String resumeTemplate;
    private int atsScore;
    private String aiFeedback;
    private LocalDateTime createdAt;
}
