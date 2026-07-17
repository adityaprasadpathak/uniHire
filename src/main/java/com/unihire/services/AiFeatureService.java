package com.unihire.services;

import com.unihire.dto.AiScoreResult;
import com.unihire.dto.ApplicationResponseDto;
import com.unihire.exceptions.UnauthorizedOperationException;
import com.unihire.models.CandidateProfile;
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

    public String generateCoverLetter(Long candidateId, Long jobId) {
        CandidateProfile candidate = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        String systemPrompt = """
                You are an expert career coach helping a candidate write a professional cover letter.
                Write a concise, professional cover letter for the candidate applying to the job below.
                Do not include placeholders like [Your Name]. Use the provided candidate details.
                """;

        String userPrompt = """
                Candidate Name: {candidateName}
                Candidate Skills: {candidateSkills}
                
                Job Title: {jobTitle}
                Company: {companyName}
                Job Description: {jobDescription}
                Job Skills: {jobSkills}
                """;

        PromptTemplate promptTemplate = new PromptTemplate(userPrompt);
        promptTemplate.add("candidateName", candidate.getFirstName() + " " + candidate.getLastName());
        promptTemplate.add("candidateSkills", candidate.getResumeUrl() != null ? "Resume Uploaded" : "Check Profile"); // Ideally we'd fetch skills from Resume text or profile
        promptTemplate.add("jobTitle", job.getTitle());
        promptTemplate.add("companyName", job.getEmployer().getCompanyName());
        promptTemplate.add("jobDescription", job.getDescription());
        promptTemplate.add("jobSkills", job.getSkills());

        return chatClient.prompt()
                .system(systemPrompt)
                .user(promptTemplate.render())
                .call()
                .content();
    }

    @Transactional
    public ApplicationResponseDto analyzeApplication(Long employerId, Long applicationId) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (!application.getJob().getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized to analyze this application");
        }

        CandidateProfile candidate = application.getCandidateProfile();
        Job job = application.getJob();

        BeanOutputConverter<AiScoreResult> converter = new BeanOutputConverter<>(AiScoreResult.class);

        String systemPrompt = """
                You are an expert strict technical recruiter. Your job is to evaluate a candidate's fit for a job.
                Analyze the candidate's skills and experience against the job description.
                You must return your response in the exact JSON format specified below.
                """;

        String userPrompt = """
                Candidate Name: {candidateName}
                
                Job Title: {jobTitle}
                Job Description: {jobDescription}
                Job Skills: {jobSkills}
                
                Provide a score from 0 to 100 representing how well the candidate matches the job.
                Provide a brief analysis (max 3 sentences) explaining the score.
                
                {format}
                """;

        PromptTemplate promptTemplate = new PromptTemplate(userPrompt);
        promptTemplate.add("candidateName", candidate.getFirstName() + " " + candidate.getLastName());
        promptTemplate.add("jobTitle", job.getTitle());
        promptTemplate.add("jobDescription", job.getDescription());
        promptTemplate.add("jobSkills", job.getSkills());
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
        } else {
            throw new RuntimeException("Failed to parse AI response");
        }

        return mapToDto(application);
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
