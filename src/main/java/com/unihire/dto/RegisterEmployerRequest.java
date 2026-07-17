package com.unihire.dto;

import lombok.Data;

@Data
public class RegisterEmployerRequest {
    private String email;
    private String password;
    private String companyName;
    private String website;
}
