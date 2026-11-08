package com.unihire.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "candidate_resume_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateResumeProfile {

    @Id
    private Long candidateId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "candidate_id")
    private CandidateProfile candidateProfile;

    @Column(columnDefinition = "TEXT")
    private String compositeProfileJson; // Stores the entire JSON structure

    private LocalDateTime lastComputedAt;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastComputedAt = LocalDateTime.now();
    }
}
