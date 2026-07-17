package com.unihire.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class CertificationDto {
    private Long id;
    private String name;
    private String issuingOrganization;
    private LocalDate issueDate;
    private String credentialUrl;
}
