package com.unihire.dto;

import lombok.Data;

@Data
public class RegisterCandidateRequest {
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String phone;
}
