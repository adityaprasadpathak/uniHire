package com.unihire.dto;

import lombok.Data;

@Data
public class PublicEmployerDto {
    private Long id;
    private String companyName;
    private String website;
    // We do not expose email or private details publicly
}
