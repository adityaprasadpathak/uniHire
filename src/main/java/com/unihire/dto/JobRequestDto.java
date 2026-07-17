package com.unihire.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobRequestDto {
    private String title;
    private String description;
    private String experience;
    private String salary;
    private String skills;
    private String location;
    private String department;
    private String employmentType;
    private Integer numberOfVacancies;
    private String benefits;
    private LocalDateTime expiryDate;
}
