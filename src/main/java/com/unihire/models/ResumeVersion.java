package com.unihire.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "resume_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_profile_id", nullable = false)
    private CandidateProfile candidateProfile;

    private String versionName; // e.g., "Java Backend", "DevOps"

    @Column(columnDefinition = "TEXT")
    private String versionDataJson; // Customized sections (skills, summary, tailored bullets)

    private String targetIndustry;
    private String targetCountry;

    // New fields for AI generation
    @Enumerated(EnumType.STRING)
    private GenerationStatus generationStatus; // PROCESSING | COMPLETED | FAILED

    private String targetRole;
    private String resumeTemplate;  // ATS_MODERN | CORPORATE | MINIMAL | EXECUTIVE | ACADEMIC
    private String resumeStyle;     // CHRONOLOGICAL | FUNCTIONAL | HYBRID
    private String errorMessage;
    private int atsScore;

    @Column(columnDefinition = "TEXT")
    private String aiFeedback;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
