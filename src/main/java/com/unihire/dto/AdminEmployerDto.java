package com.unihire.dto;

import com.unihire.models.VerificationStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminEmployerDto {
    private Long id;
    private String email;
    private String phone;
    private Boolean isLocked;
    private LocalDateTime lockedAt;
    private String lockReason;
    private LocalDateTime createdAt;
    
    private String companyName;
    private String gstDetails;
    private String website;
    private String companyProfile;
    private VerificationStatus verificationStatus;
}
