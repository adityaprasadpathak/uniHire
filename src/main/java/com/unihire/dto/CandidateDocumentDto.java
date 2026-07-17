package com.unihire.dto;

import com.unihire.models.DocumentType;
import com.unihire.models.VerificationStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CandidateDocumentDto {
    private Long id;
    private Long candidateId;
    private DocumentType documentType;
    private String url;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private VerificationStatus status;
    private LocalDateTime uploadedAt;
    private LocalDateTime verifiedAt;
    private String verifiedBy;
}
