package com.unihire.dto;

import com.unihire.models.VerificationStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApplicationResponseDto {
    private Long id;
    
    // Job details
    private Long jobId;
    private String jobTitle;
    private String companyName;

    // Candidate details
    private Long candidateId;
    private String candidateName;
    private String candidateEmail;
    private String candidatePhone;
    private String resumeUrl;
    private Integer profileCompletionScore;
    private VerificationStatus candidateVerificationStatus;
    
    // Application details
    private String status;
    private String coverLetter;
    private LocalDateTime appliedAt;
    private LocalDateTime updatedAt;
    // AI Feature Fields
    private Integer aiMatchScore;
    private String aiFitAnalysis;
    private String aiModel;
    private String aiPromptVersion;
    private LocalDateTime aiScoredAt;
}
