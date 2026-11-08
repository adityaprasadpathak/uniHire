package com.unihire.services.ai;

import com.unihire.dto.AiScoreResult;
import com.unihire.dto.ApplicationResponseDto;
import com.unihire.exceptions.UnauthorizedOperationException;
import com.unihire.models.CandidateProfile;
import com.unihire.models.Experience;
import com.unihire.models.Education;
import com.unihire.models.Job;
import com.unihire.models.JobApplication;
import com.unihire.repositories.CandidateProfileRepository;
import com.unihire.repositories.JobApplicationRepository;
import com.unihire.repositories.JobRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiFeatureService {

    private final ChatClient chatClient;
    private final JobRepository jobRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final JobApplicationRepository applicationRepository;

    private static final String MODEL_NAME = "ollama-local"; // Or retrieve from properties
    private static final String PROMPT_VERSION = "v1.0";

    public AiFeatureService(ChatClient.Builder chatClientBuilder,
                            JobRepository jobRepository,
                            CandidateProfileRepository candidateProfileRepository,
                            JobApplicationRepository applicationRepository) {
        this.chatClient = chatClientBuilder.build();
        this.jobRepository = jobRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.applicationRepository = applicationRepository;
    }

    private String buildCandidateProfileSummary(CandidateProfile profile) {
        StringBuilder summary = new StringBuilder();
        summary.append("Name: ").append(profile.getFirstName()).append(" ").append(profile.getLastName()).append("\n");
        if (profile.getSkills() != null && !profile.getSkills().isEmpty()) {
            summary.append("Skills: ").append(String.join(", ", profile.getSkills())).append("\n");
        }
        if (profile.getExperiences() != null && !profile.getExperiences().isEmpty()) {
            summary.append("Professional Experience:\n");
            for (Experience exp : profile.getExperiences()) {
                summary.append("- ").append(exp.getJobTitle()).append(" at ").append(exp.getCompany());
                if (exp.getStartDate() != null || exp.getEndDate() != null) {
                    summary.append(" (").append(exp.getStartDate() != null ? exp.getStartDate() : "").append(" to ")
                           .append(exp.getEndDate() != null ? exp.getEndDate() : "Present").append(")");
                }
                summary.append("\n");
                if (exp.getDescription() != null && !exp.getDescription().trim().isEmpty()) {
                    summary.append("  Description: ").append(exp.getDescription()).append("\n");
                }
            }
        }
        if (profile.getEducations() != null && !profile.getEducations().isEmpty()) {
            summary.append("Education:\n");
            for (Education edu : profile.getEducations()) {
                summary.append("- ").append(edu.getDegree()).append(" from ").append(edu.getInstitution());
                if (edu.getStartDate() != null || edu.getEndDate() != null) {
                    summary.append(" (").append(edu.getStartDate() != null ? edu.getStartDate() : "").append(" to ")
                           .append(edu.getEndDate() != null ? edu.getEndDate() : "Present").append(")");
                }
                summary.append("\n");
            }
        }
        return summary.toString();
    }

    public String generateCoverLetter(Long candidateId, Long jobId) {
        CandidateProfile candidate = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate profile not found"));
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        try {
            String systemPrompt = """
                    You are an expert career coach helping a candidate write a professional cover letter.
                    Write a concise, professional cover letter for the candidate applying to the job below.
                    Do not include placeholders like [Your Name]. Use the provided candidate details.
                    """;

            String userPrompt = """
                    Candidate Background:
                    {candidateProfile}
                    
                    Job Title: {jobTitle}
                    Company: {companyName}
                    Job Description: {jobDescription}
                    Job Skills: {jobSkills}
                    """;

            PromptTemplate promptTemplate = new PromptTemplate(userPrompt);
            promptTemplate.add("candidateProfile", buildCandidateProfileSummary(candidate));
            promptTemplate.add("jobTitle", job.getTitle());
            promptTemplate.add("companyName", job.getEmployer() != null ? job.getEmployer().getCompanyName() : "");
            promptTemplate.add("jobDescription", job.getDescription() != null ? job.getDescription() : "");
            promptTemplate.add("jobSkills", job.getSkills() != null ? job.getSkills() : "");

            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(promptTemplate.render())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("LLM cover letter generation failed, using smart template fallback: {}", e.getMessage());
            return buildFallbackCoverLetter(candidate, job);
        }
    }

    private String buildFallbackCoverLetter(CandidateProfile candidate, Job job) {
        String name = ((candidate.getFirstName() != null ? candidate.getFirstName() : "") + " " +
                (candidate.getLastName() != null ? candidate.getLastName() : "")).trim();
        if (name.isEmpty()) name = "Candidate";

        String company = job.getEmployer() != null && job.getEmployer().getCompanyName() != null 
                ? job.getEmployer().getCompanyName() : "your team";

        String skills = candidate.getSkills() != null && !candidate.getSkills().isEmpty()
                ? String.join(", ", candidate.getSkills())
                : "software engineering and technical problem solving";

        return String.format(
            "Dear Hiring Manager at %s,\n\n" +
            "I am writing to express my strong interest in the %s role. With my technical background and practical experience in %s, I am eager to contribute effectively to your organization's goals.\n\n" +
            "My experience has equipped me with strong problem-solving skills, adaptability, and a commitment to high engineering standards. I am confident that my skills align well with the expectations for this role at %s.\n\n" +
            "Thank you for considering my application. I look forward to discussing how I can add value to your team.\n\n" +
            "Sincerely,\n%s",
            company, job.getTitle(), skills, company, name
        );
    }

    @Transactional
    public ApplicationResponseDto analyzeApplication(Long employerId, Long applicationId) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (!application.getJob().getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized to analyze this application");
        }

        evaluateApplication(application);
        return mapToDto(application);
    }

    @Transactional
    public void evaluateApplication(JobApplication application) {
        CandidateProfile candidate = application.getCandidateProfile();
        Job job = application.getJob();

        try {
            BeanOutputConverter<AiScoreResult> converter = new BeanOutputConverter<>(AiScoreResult.class);

            String systemPrompt = """
                    You are an expert strict technical recruiter. Your job is to evaluate a candidate's fit for a job.
                    Analyze the candidate's skills and experience against the job description.
                    You must return your response in the exact JSON format specified below.
                    """;

            String userPrompt = """
                    Candidate Background:
                    {candidateProfile}
                    
                    Job Title: {jobTitle}
                    Job Description: {jobDescription}
                    Job Skills: {jobSkills}
                    
                    Provide a score from 0 to 100 representing how well the candidate matches the job.
                    Provide a brief analysis (max 3 sentences) explaining the score.
                    
                    {format}
                    """;

            PromptTemplate promptTemplate = new PromptTemplate(userPrompt);
            promptTemplate.add("candidateProfile", buildCandidateProfileSummary(candidate));
            promptTemplate.add("jobTitle", job.getTitle());
            promptTemplate.add("jobDescription", job.getDescription() != null ? job.getDescription() : "");
            promptTemplate.add("jobSkills", job.getSkills() != null ? job.getSkills() : "");
            promptTemplate.add("format", converter.getFormat());

            log.info("Starting AI ATS resume scoring for Application ID: {}, Candidate: {} {}, Job: {}", 
                     application.getId(), candidate.getFirstName(), candidate.getLastName(), job.getTitle());

            String jsonResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(promptTemplate.render())
                    .call()
                    .content();

            AiScoreResult result = converter.convert(jsonResponse);

            if (result != null) {
                log.info("Received AI ATS score: {} for Application ID: {}", result.score(), application.getId());
                application.setAiMatchScore(result.score());
                application.setAiFitAnalysis(result.analysis());
                application.setAiModel(MODEL_NAME);
                application.setAiPromptVersion(PROMPT_VERSION);
                application.setAiScoredAt(LocalDateTime.now());
                applicationRepository.save(application);
                return;
            }
        } catch (Exception e) {
            log.warn("AI LLM evaluation failed for application {}, falling back to algorithmic ATS evaluation: {}", application.getId(), e.getMessage());
        }

        evaluateApplicationAlgorithmic(application);
    }

    private void evaluateApplicationAlgorithmic(JobApplication application) {
        CandidateProfile candidate = application.getCandidateProfile();
        Job job = application.getJob();

        List<String> jobSkills = parseCsvSkills(job.getSkills());
        List<String> candidateSkills = candidate.getSkills() != null ? candidate.getSkills() : List.of();
        List<String> candidateSkillsLower = candidateSkills.stream().map(String::toLowerCase).toList();

        int matchedCount = 0;
        for (String skill : jobSkills) {
            if (candidateSkillsLower.stream().anyMatch(cs -> cs.contains(skill.toLowerCase()) || skill.toLowerCase().contains(cs))) {
                matchedCount++;
            }
        }

        int score = jobSkills.isEmpty() ? 75 : (int) Math.round(((double) matchedCount / jobSkills.size()) * 100.0);
        score = Math.max(15, Math.min(98, score));

        String analysis = String.format(
            "Matches %d of %d required skills (%s). Profile completion score is %d%%.",
            matchedCount, jobSkills.size(),
            jobSkills.isEmpty() ? "general fit" : String.join(", ", jobSkills.subList(0, Math.min(3, jobSkills.size()))),
            candidate.getProfileCompletionScore() != null ? candidate.getProfileCompletionScore() : 50
        );

        application.setAiMatchScore(score);
        application.setAiFitAnalysis(analysis);
        application.setAiModel("algorithmic-ats-v1");
        application.setAiPromptVersion(PROMPT_VERSION);
        application.setAiScoredAt(LocalDateTime.now());
        applicationRepository.save(application);
    }

    private List<String> parseCsvSkills(String skillsCsv) {
        if (skillsCsv == null || skillsCsv.isBlank()) return List.of();
        return java.util.Arrays.stream(skillsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private ApplicationResponseDto mapToDto(JobApplication application) {
        ApplicationResponseDto dto = new ApplicationResponseDto();
        dto.setId(application.getId());
        dto.setJobId(application.getJob().getId());
        dto.setJobTitle(application.getJob().getTitle());
        dto.setCompanyName(application.getJob().getEmployer().getCompanyName());
        dto.setCandidateId(application.getCandidateProfile().getUser().getId());
        dto.setCandidateName(application.getCandidateProfile().getFirstName() + " " + application.getCandidateProfile().getLastName());
        dto.setCandidateEmail(application.getCandidateProfile().getUser().getEmail());
        dto.setCandidatePhone(application.getCandidateProfile().getPhone());
        dto.setResumeUrl(application.getCandidateProfile().getResumeUrl());
        dto.setProfileCompletionScore(application.getCandidateProfile().getProfileCompletionScore());
        dto.setStatus(application.getStatus().name());
        dto.setCoverLetter(application.getCoverLetter());
        dto.setAppliedAt(application.getAppliedAt());
        dto.setUpdatedAt(application.getUpdatedAt());
        dto.setAiMatchScore(application.getAiMatchScore());
        dto.setAiFitAnalysis(application.getAiFitAnalysis());
        dto.setAiModel(application.getAiModel());
        dto.setAiPromptVersion(application.getAiPromptVersion());
        dto.setAiScoredAt(application.getAiScoredAt());
        return dto;
    }
}
