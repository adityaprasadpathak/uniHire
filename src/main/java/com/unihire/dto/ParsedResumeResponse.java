package com.unihire.dto;

import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
public class ParsedResumeResponse {
    
    private String firstName;
    private String lastName;
    private String phone;
    private String summary;
    private String linkedin;
    private String location;
    private String currentTitle;
    private String currentCompany;
    private Integer totalExperienceYears;
    private String languages;
    
    private List<String> skills = new ArrayList<>();
    
    private List<ParsedEducation> education = new ArrayList<>();
    private List<ParsedExperience> experience = new ArrayList<>();
    private List<ParsedCertification> certifications = new ArrayList<>();
    
    @Data
    public static class ParsedEducation {
        private String degree;
        private String institution;
        private String startDate; // ISO String or Year
        private String endDate;
        private String description;
    }
    
    @Data
    public static class ParsedExperience {
        private String jobTitle;
        private String company;
        private String startDate; // ISO String or Year-Month
        private String endDate;
        private String description;
    }
    
    @Data
    public static class ParsedCertification {
        private String name;
        private String issuingOrganization;
        private String issueDate; // ISO String or Year-Month
    }
}
