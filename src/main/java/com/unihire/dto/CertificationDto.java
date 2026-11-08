package com.unihire.dto;

import lombok.Data;


@Data
public class CertificationDto {
    private Long id;
    private String name;
    private String issuingOrganization;
    private String issueDate;
    private String credentialUrl;
}
