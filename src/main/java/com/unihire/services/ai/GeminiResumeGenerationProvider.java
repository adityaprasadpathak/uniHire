package com.unihire.services.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihire.dto.ResumeEnhancementResult;
import com.unihire.dto.TargetCustomizationRequest;
import com.unihire.models.CandidateProfile;
import com.unihire.models.Experience;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "resume.ai.provider", havingValue = "gemini")
public class GeminiResumeGenerationProvider implements ResumeGenerationProvider {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public GeminiResumeGenerationProvider(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public ResumeEnhancementResult enhance(CandidateProfile profile, TargetCustomizationRequest request) {
        BeanOutputConverter<ResumeEnhancementResult> converter = new BeanOutputConverter<>(ResumeEnhancementResult.class);
        
        String experiencesJson = "[]";
        String skillsStr = "";
        try {
            if (profile.getExperiences() != null) {
                experiencesJson = objectMapper.writeValueAsString(profile.getExperiences().stream().map(exp -> 
                    Map.of("experienceId", exp.getId(), "company", exp.getCompany(), "jobTitle", exp.getJobTitle(), "description", exp.getDescription() != null ? exp.getDescription() : "")
                ).collect(Collectors.toList()));
            }
            if (profile.getSkills() != null) {
                skillsStr = String.join(", ", profile.getSkills());
            }
        } catch (Exception ignored) {}

        String promptText = """
                You are a world-class executive resume writer and ATS optimization expert.
                Your goal is to tailor the candidate's resume for a specific target.
                
                TARGET INFO:
                - Industry: {industry}
                - Target Role: {targetRole}
                - Country/Market: {country}
                - Experience Level: {experienceLevel}
                - Job Description (if any): {jobDescription}
                
                CANDIDATE CURRENT INFO:
                - Current Skills: {skills}
                - Current Experiences: {experiences}
                
                INSTRUCTIONS:
                1. Write an 'enhancedSummary' (1 paragraph) that positions the candidate perfectly for the Target Role in the specified Industry and Country.
                2. For EACH experience in 'Current Experiences', create an 'EnhancedExperience' object.
                   - Keep the original 'experienceId', 'company', and 'jobTitle'.
                   - Rewrite the 'enhancedBullets' (as a single string with newline '\\n' separated bullets).
                   - CRITICAL: Do NOT just list keywords at the end of bullets. Weave industry keywords and (if provided) Job Description keywords NATURALLY into the bullet points.
                   - Quantify achievements where possible (e.g., "improved efficiency by 25%").
                3. Provide 'suggestedKeywords' (5-8 high-value ATS keywords for this role/industry).
                4. Reorder and refine the candidate's skills into 'tailoredSkills' (max 15), placing the most relevant ones first.
                5. Provide a short 'aiFeedback' message explaining what major changes you made to align with the target.
                
                {format}
                """;

        PromptTemplate template = new PromptTemplate(promptText);
        template.add("industry", request.getIndustry() != null ? request.getIndustry() : "Any");
        template.add("targetRole", request.getTargetRole() != null ? request.getTargetRole() : "Professional");
        template.add("country", request.getCountry() != null ? request.getCountry() : "Global");
        template.add("experienceLevel", request.getExperienceLevel() != null ? request.getExperienceLevel() : "Mid-Level");
        template.add("jobDescription", request.getJobDescription() != null && !request.getJobDescription().isBlank() ? request.getJobDescription() : "None provided");
        template.add("skills", skillsStr);
        template.add("experiences", experiencesJson);
        template.add("format", converter.getFormat());

        String responseContent = chatClient.prompt(template.create()).call().content();
        
        return converter.convert(responseContent);
    }
}
