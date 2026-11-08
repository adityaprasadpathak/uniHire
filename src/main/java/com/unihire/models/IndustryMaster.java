package com.unihire.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import java.util.List;

@Entity
public class IndustryMaster {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String industryName; // e.g., "IT", "Banking", "Healthcare"

    @Column(columnDefinition = "TEXT")
    private String recommendedSkills; // comma separated or JSON

    @Column(columnDefinition = "TEXT")
    private String domainKeywords; // comma separated or JSON

    @Column(columnDefinition = "TEXT")
    private String requiredSections; // comma separated sections needed for this industry

    public IndustryMaster() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIndustryName() {
        return industryName;
    }

    public void setIndustryName(String industryName) {
        this.industryName = industryName;
    }

    public String getRecommendedSkills() {
        return recommendedSkills;
    }

    public void setRecommendedSkills(String recommendedSkills) {
        this.recommendedSkills = recommendedSkills;
    }

    public String getDomainKeywords() {
        return domainKeywords;
    }

    public void setDomainKeywords(String domainKeywords) {
        this.domainKeywords = domainKeywords;
    }

    public String getRequiredSections() {
        return requiredSections;
    }

    public void setRequiredSections(String requiredSections) {
        this.requiredSections = requiredSections;
    }
}
