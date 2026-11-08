package com.unihire.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private EmployerProfile employer;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String experience;
    private Integer minExperience;
    private Integer maxExperience;
    private String salary;
    
    @Column(columnDefinition = "TEXT")
    private String skills;
    
    @Column(columnDefinition = "TEXT")
    private String preferredSkills;
    
    private String location;
    private String department;
    private String employmentType;
    private Integer numberOfVacancies;
    
    // Additional recruitment fields
    private String jobLevel;
    private String workMode;
    private Integer openings;
    private String education;
    private String certifications;
    private String languages;
    
    @Column(columnDefinition = "TEXT")
    private String responsibilities;
    
    @Column(columnDefinition = "TEXT")
    private String qualifications;
    
    @Column(columnDefinition = "TEXT")
    private String benefits;

    private String workingDays;
    private String shiftTiming;
    private String travelRequirement;
    private String noticePeriod;
    private String employmentCategory;
    private String industry;
    private String incentives;
    private String hiringManager;
    private String recruiter;

    @Column(columnDefinition = "TEXT")
    private String interviewProcess;

    @Column(columnDefinition = "TEXT")
    private String screeningQuestions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.DRAFT;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    
    private LocalDateTime expiryDate;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
