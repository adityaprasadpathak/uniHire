package com.unihire.dto;

import lombok.Data;
import com.unihire.models.VerificationStatus;
import java.util.List;

@Data
public class CandidateProfileDto {
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private List<String> skills;
    private String languages;
    private String portfolioUrl;
    private String resumeUrl;
    private String parsingStatus;
    private String parsingFailureReason;
    private Integer profileCompletionScore;
    
    private List<EducationDto> educations;
    private List<ExperienceDto> experiences;
    private List<CertificationDto> certifications;
    
    private VerificationStatus verificationStatus;
    private List<CandidateDocumentDto> documents;
}
