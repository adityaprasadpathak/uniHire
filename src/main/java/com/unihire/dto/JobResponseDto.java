package com.unihire.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobResponseDto {
    private Long id;
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
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime closedAt;
    private LocalDateTime expiryDate;
}
