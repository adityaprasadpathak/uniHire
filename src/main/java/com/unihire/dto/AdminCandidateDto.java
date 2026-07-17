package com.unihire.dto;

import com.unihire.models.VerificationStatus;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminCandidateDto {
    private Long id;
    private String email;
    private String phone;
    private Boolean isLocked;
    private LocalDateTime lockedAt;
    private String lockReason;
    private LocalDateTime createdAt;
    
    private String firstName;
    private String lastName;
    private VerificationStatus verificationStatus;
    private List<CandidateDocumentDto> documents;
}
