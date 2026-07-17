package com.unihire.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String firstName;
    private String lastName;
    private String phone;
    private java.util.List<String> skills;
    private String languages;
    private String portfolioUrl;
}
