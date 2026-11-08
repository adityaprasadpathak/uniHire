package com.unihire.services.ai;

import com.unihire.dto.EnhancedExperience;
import com.unihire.dto.ResumeEnhancementResult;
import com.unihire.dto.TargetCustomizationRequest;
import com.unihire.models.CandidateProfile;
import com.unihire.models.Experience;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@ConditionalOnProperty(name = "resume.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockResumeGenerationProvider implements ResumeGenerationProvider {

    private static final Map<String, List<String>> industryKeywords = Map.of(
            "Information Technology", List.of("microservices", "REST APIs", "CI/CD", "cloud infrastructure", "agile", "scalability", "DevOps", "containerization"),
            "Banking & Finance", List.of("risk management", "P&L", "compliance", "Basel III", "KYC", "AML", "portfolio management", "financial modelling"),
            "Healthcare", List.of("HIPAA", "patient outcomes", "clinical workflows", "EHR systems", "interoperability", "medical coding", "regulatory compliance"),
            "Manufacturing", List.of("lean manufacturing", "Six Sigma", "supply chain", "OEE", "process optimization", "ISO 9001", "kaizen")
    );

    private static final Map<String, List<String>> industryVerbs = Map.of(
            "Information Technology", List.of("Engineered", "Architected", "Optimized", "Deployed", "Automated", "Refactored", "Spearheaded"),
            "Banking & Finance", List.of("Managed", "Streamlined", "Mitigated", "Executed", "Audited", "Analyzed", "Structured"),
            "Healthcare", List.of("Implemented", "Coordinated", "Improved", "Standardized", "Monitored", "Enhanced", "Facilitated"),
            "Manufacturing", List.of("Reduced", "Improved", "Implemented", "Optimized", "Led", "Coordinated", "Achieved")
    );

    private static final List<String> defaultKeywords = List.of(
            "leadership", "cross-functional collaboration", "stakeholder management", "problem-solving", "communication"
    );

    private static final List<String> defaultVerbs = List.of(
            "Led", "Delivered", "Developed", "Improved", "Collaborated on"
    );

    private final Random random = new Random();

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public ResumeEnhancementResult enhance(CandidateProfile profile, TargetCustomizationRequest request) {
        String industry = request.getIndustry() != null ? request.getIndustry() : "Information Technology";
        String targetRole = request.getTargetRole() != null ? request.getTargetRole() : "Professional";
        String country = request.getCountry() != null ? request.getCountry() : "India";

        List<String> keywords = new ArrayList<>(industryKeywords.getOrDefault(industry, defaultKeywords));
        if (request.getExtraKeywords() != null) {
            keywords.addAll(request.getExtraKeywords());
        }

        List<String> verbs = industryVerbs.getOrDefault(industry, defaultVerbs);

        // Build enhanced experiences
        List<EnhancedExperience> enhancedExperiences = new ArrayList<>();
        if (profile.getExperiences() != null) {
            for (Experience exp : profile.getExperiences()) {
                EnhancedExperience enhanced = new EnhancedExperience();
                enhanced.setExperienceId(exp.getId());
                enhanced.setCompany(exp.getCompany());
                enhanced.setJobTitle(exp.getJobTitle());

                StringBuilder bulletsBuilder = new StringBuilder();
                bulletsBuilder.append(pickVerb(verbs)).append(" high-impact ").append(exp.getJobTitle())
                        .append(" solutions at ").append(exp.getCompany())
                        .append(", improving operational efficiency by ").append(15 + random.nextInt(30)).append("%.\n");

                bulletsBuilder.append(pickVerb(verbs)).append(" cross-functional teams to implement ")
                        .append(keywords.get(random.nextInt(keywords.size())))
                        .append(" strategies, reducing ").append(industry.contains("Banking") ? "risk exposure" : "delivery time")
                        .append(" by ").append(10 + random.nextInt(25)).append("%.\n");

                bulletsBuilder.append(pickVerb(verbs)).append(" ")
                        .append(keywords.size() > 1 ? keywords.get(1) : keywords.get(0))
                        .append(" practices across ").append(exp.getCompany())
                        .append(", resulting in ").append(20 + random.nextInt(30)).append("% improvement in ").append(pickOutcome(industry)).append(".\n");

                if (exp.getDescription() != null && !exp.getDescription().isBlank()) {
                    bulletsBuilder.append(pickVerb(verbs)).append(" key initiatives: ").append(
                            exp.getDescription().length() > 120 ? exp.getDescription().substring(0, 120) + "..." : exp.getDescription()
                    ).append(".\n");
                }

                enhanced.setEnhancedBullets(bulletsBuilder.toString().trim());
                enhancedExperiences.add(enhanced);
            }
        }

        // Build tailored skills: put industry keywords first, then profile skills
        List<String> tailoredSkills = new ArrayList<>(keywords.subList(0, Math.min(6, keywords.size())));
        if (profile.getSkills() != null) {
            for (String skill : profile.getSkills()) {
                if (!tailoredSkills.contains(skill)) {
                    tailoredSkills.add(skill);
                }
            }
        }

        // Build professional summary
        String candidateName = "";
        if (profile.getFirstName() != null) {
            candidateName = profile.getFirstName() + (profile.getLastName() != null ? " " + profile.getLastName() : "");
        }
        String summary = buildSummary(candidateName, targetRole, industry, country, keywords);

        // AI Feedback
        String aiFeedback = String.format(
                "Resume tailored for %s role in %s industry (%s market). Added %d industry-specific keywords. " +
                "Bullet points rewritten with quantifiable impact metrics. Skills reordered for ATS optimization.",
                targetRole, industry, country, keywords.size()
        );

        return ResumeEnhancementResult.builder()
                .enhancedSummary(summary)
                .enhancedExperiences(enhancedExperiences)
                .suggestedKeywords(keywords)
                .tailoredSkills(tailoredSkills)
                .aiFeedback(aiFeedback)
                .build();
    }

    private String buildSummary(String name, String targetRole, String industry, String country, List<String> keywords) {
        String kw1 = keywords.size() > 0 ? keywords.get(0) : "industry best practices";
        String kw2 = keywords.size() > 1 ? keywords.get(1) : "operational excellence";
        return String.format(
                "Results-driven %s professional with proven expertise in %s and %s within the %s sector. " +
                "Adept at delivering scalable, high-quality solutions in fast-paced environments across %s markets. " +
                "Demonstrated track record of cross-functional collaboration, stakeholder engagement, and measurable business impact.",
                targetRole, kw1, kw2, industry, country
        );
    }

    private String pickVerb(List<String> verbs) {
        return verbs.get(random.nextInt(verbs.size()));
    }

    private String pickOutcome(String industry) {
        return switch (industry) {
            case "Banking & Finance" -> "compliance adherence";
            case "Healthcare" -> "patient outcomes";
            case "Manufacturing" -> "production throughput";
            default -> "system throughput";
        };
    }
}
