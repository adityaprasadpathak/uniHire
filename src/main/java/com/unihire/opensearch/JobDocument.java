package com.unihire.opensearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobDocument {

    private String id; // Job.getId().toString()
    private String title;
    private String description;
    private String experience;
    private String salary;
    private String skills;
    private String location;
    private String department;
    private String employmentType;
    private String status;
    private LocalDateTime expiryDate;
    private LocalDateTime publishedAt;
    private String companyName;
    private Long employerId;
}
