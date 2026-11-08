package com.unihire.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TargetCustomizationRequest {

    private String industry;           // e.g. "Information Technology", "Banking & Finance"
    private String country;            // e.g. "India", "US", "Europe"
    private String jobDescription;     // full JD text
    private String targetRole;         // e.g. "Senior Software Engineer"
    private String experienceLevel;    // FRESHER | MID | SENIOR | EXECUTIVE
    private String resumeTemplate;     // ATS_MODERN | CORPORATE | MINIMAL | EXECUTIVE | ACADEMIC | GOVERNMENT
    private String language;           // en | de | fr (default en)
    private String targetCompany;      // optional
    private List<String> extraKeywords;
    private String resumeStyle;        // CHRONOLOGICAL | FUNCTIONAL | HYBRID
    private boolean includeProjects;
    private boolean includeCertifications;
    private int pageLimit;             // 1 or 2, default 2
    private String exportFormat;       // PDF | DOCX | JSON
}
