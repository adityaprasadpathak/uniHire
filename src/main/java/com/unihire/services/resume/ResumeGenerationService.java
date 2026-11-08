package com.unihire.services.resume;

import com.unihire.dto.EducationSection;
import com.unihire.dto.EnhancedExperience;
import com.unihire.dto.ExperienceSection;
import com.unihire.dto.ResumeEnhancementResult;
import com.unihire.dto.ResumeVersionDataDto;
import com.unihire.dto.TargetCustomizationRequest;
import com.unihire.models.CandidateProfile;
import com.unihire.models.Education;
import com.unihire.models.Experience;
import com.unihire.services.ai.ResumeGenerationProvider;
import com.unihire.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ResumeGenerationService {

    @Autowired
    private List<ResumeGenerationProvider> providers;

    @Value("${resume.ai.provider:mock}")
    private String activeProviderName;

    public ResumeVersionDataDto generateVersionData(CandidateProfile profile, TargetCustomizationRequest request) {
        // Select provider by name
        ResumeGenerationProvider provider = providers.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(activeProviderName))
                .findFirst()
                .orElseGet(() -> providers.stream()
                        .filter(p -> p.getProviderName().equalsIgnoreCase("mock"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No resume generation provider found")));

        ResumeEnhancementResult result = provider.enhance(profile, request);

        // Build a lookup map from experienceId -> EnhancedExperience for O(1) access
        Map<Long, EnhancedExperience> enhancedMap = result.getEnhancedExperiences() != null
                ? result.getEnhancedExperiences().stream()
                        .collect(Collectors.toMap(EnhancedExperience::getExperienceId, Function.identity()))
                : Map.of();

        // Map experiences
        List<ExperienceSection> experienceSections = new ArrayList<>();
        if (profile.getExperiences() != null) {
            for (Experience exp : profile.getExperiences()) {
                ExperienceSection section = new ExperienceSection();
                section.setCompany(exp.getCompany());
                section.setJobTitle(exp.getJobTitle());
                section.setStartDate(DateUtils.formatMonthYear(exp.getStartDate()));
                section.setEndDate(DateUtils.formatMonthYear(exp.getEndDate()));

                // Use AI-enhanced bullets if available, else fall back to original description
                if (enhancedMap.containsKey(exp.getId())) {
                    String rawBullets = enhancedMap.get(exp.getId()).getEnhancedBullets();
                    section.setBullets(parseBullets(rawBullets));
                } else if (exp.getDescription() != null && !exp.getDescription().isBlank()) {
                    section.setBullets(parseBullets(exp.getDescription()));
                } else {
                    section.setBullets(List.of());
                }
                experienceSections.add(section);
            }
        }

        // Map educations
        List<EducationSection> educationSections = new ArrayList<>();
        if (profile.getEducations() != null) {
            for (Education edu : profile.getEducations()) {
                EducationSection section = new EducationSection();
                section.setDegree(edu.getDegree());
                section.setInstitution(edu.getInstitution());
                section.setStartDate(DateUtils.formatMonthYear(edu.getStartDate()));
                section.setEndDate(DateUtils.formatMonthYear(edu.getEndDate()));
                section.setDescription(edu.getDescription());
                educationSections.add(section);
            }
        }

        // Map certifications (if requested)
        List<String> certifications = new ArrayList<>();
        if (request.isIncludeCertifications() && profile.getCertifications() != null) {
            profile.getCertifications().forEach(cert -> {
                String label = cert.getName() + " — " + cert.getIssuingOrganization();
                if (cert.getIssueDate() != null) label += " (" + cert.getIssueDate() + ")";
                certifications.add(label);
            });
        }

        // Merge AI keywords with extra keywords from request
        List<String> keywords = new ArrayList<>();
        if (result.getSuggestedKeywords() != null) keywords.addAll(result.getSuggestedKeywords());
        if (request.getExtraKeywords() != null) {
            request.getExtraKeywords().forEach(kw -> {
                if (!keywords.contains(kw)) keywords.add(kw);
            });
        }

        // Candidate name & contact from profile
        String candidateName = buildFullName(profile);
        String email = profile.getUser() != null ? profile.getUser().getEmail() : null;
        String phone = profile.getPhone();
        String location = null; // location not stored directly on profile

        return ResumeVersionDataDto.builder()
                .candidateName(candidateName)
                .email(email)
                .phone(phone)
                .location(location)
                .professionalSummary(result.getEnhancedSummary())
                .skills(result.getTailoredSkills() != null ? result.getTailoredSkills() : profile.getSkills())
                .experiences(experienceSections)
                .educations(educationSections)
                .certifications(certifications)
                .keywords(keywords)
                .targetIndustry(request.getIndustry())
                .targetCountry(request.getCountry())
                .targetRole(request.getTargetRole())
                .resumeTemplate(request.getResumeTemplate() != null ? request.getResumeTemplate() : "ATS_MODERN")
                .resumeStyle(request.getResumeStyle() != null ? request.getResumeStyle() : "CHRONOLOGICAL")
                .aiFeedback(result.getAiFeedback())
                .build();
    }

    private List<String> parseBullets(String rawBullets) {
        if (rawBullets == null || rawBullets.isBlank()) return List.of();
        return Arrays.stream(rawBullets.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    private String buildFullName(CandidateProfile profile) {
        if (profile.getFirstName() == null && profile.getLastName() == null) return null;
        StringBuilder sb = new StringBuilder();
        if (profile.getFirstName() != null) sb.append(profile.getFirstName());
        if (profile.getLastName() != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(profile.getLastName());
        }
        return sb.toString();
    }
}
