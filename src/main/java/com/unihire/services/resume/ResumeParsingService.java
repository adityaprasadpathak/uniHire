package com.unihire.services.resume;
import com.unihire.services.storage.StorageService;
import com.unihire.services.ai.RecommendationService;

import com.unihire.dto.ParsedResumeResponse;
import com.unihire.exceptions.BadRequestException;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.models.*;
import com.unihire.repositories.CandidateProfileRepository;
import com.unihire.repositories.CertificationRepository;
import com.unihire.repositories.EducationRepository;
import com.unihire.repositories.ExperienceRepository;
import com.unihire.services.extractor.DocumentTextExtractor;
import com.unihire.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.util.List;

@Service
public class ResumeParsingService {

    private static final Logger logger = LoggerFactory.getLogger(ResumeParsingService.class);
    private final CandidateProfileRepository candidateProfileRepository;
    private final EducationRepository educationRepository;
    private final ExperienceRepository experienceRepository;
    private final CertificationRepository certificationRepository;
    private final StorageService storageService;
    private final DocumentTextExtractor textExtractor;
    private final ChatClient chatClient;
    private final CacheManager cacheManager;
    private final RecommendationService recommendationService;
    private final TransactionTemplate transactionTemplate;

    public ResumeParsingService(CandidateProfileRepository candidateProfileRepository,
                                EducationRepository educationRepository,
                                ExperienceRepository experienceRepository,
                                CertificationRepository certificationRepository,
                                StorageService storageService,
                                DocumentTextExtractor textExtractor,
                                ChatClient.Builder chatClientBuilder,
                                CacheManager cacheManager,
                                RecommendationService recommendationService,
                                PlatformTransactionManager transactionManager) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.educationRepository = educationRepository;
        this.experienceRepository = experienceRepository;
        this.certificationRepository = certificationRepository;
        this.storageService = storageService;
        this.textExtractor = textExtractor;
        this.chatClient = chatClientBuilder.build();
        this.cacheManager = cacheManager;
        this.recommendationService = recommendationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async("taskExecutor")
    public void parseResume(Long candidateId, String filename) {
        logger.info("Background Job Started: Parsing resume for Candidate ID: {} from file: {}", candidateId, filename);

        // STEP 1: Immediately commit PROCESSING status so concurrent UI polling reflects active progress
        Boolean profileFound = transactionTemplate.execute(status -> {
            var profileOpt = candidateProfileRepository.findById(candidateId);
            if (profileOpt.isPresent()) {
                CandidateProfile p = profileOpt.get();
                p.setParsingStatus(ParsingStatus.PROCESSING);
                candidateProfileRepository.save(p);
                return true;
            }
            return false;
        });

        if (!Boolean.TRUE.equals(profileFound)) {
            logger.warn("Candidate profile not found for ID: {}", candidateId);
            return;
        }

        // Evict cache immediately so getParsingStatus or getProfile returns PROCESSING
        evictCandidateCaches(candidateId);

        ParsedResumeResponse parsedData = null;
        String failureReason = null;

        try {
            logger.info("[STEP 1/4] Downloading file from storage: {}", filename);
            InputStream fileStream = storageService.getFileStream(filename);
            
            logger.info("[STEP 2/4] Extracting text from document using Apache Tika...");
            String resumeText = textExtractor.extractText(fileStream, filename);
            
            if (resumeText == null || resumeText.trim().isEmpty()) {
                throw new BadRequestException("Resume contained no extractable text.");
            }

            logger.info("Successfully extracted {} characters of text.", resumeText.length());
            logger.info("[STEP 3/4] Sending text to AI for structured extraction...");

            BeanOutputConverter<ParsedResumeResponse> converter = new BeanOutputConverter<>(ParsedResumeResponse.class);
            
            String promptString = """
                    Extract the candidate information from the following resume text.
                    Return ONLY valid JSON matching the exact schema requested.
                    Do not explain.
                    If a field is missing, use null.
                    If no skills exist, return [].
                    Never invent data.
                    Do NOT include the day for any dates. Extract or format all start and end dates for experiences and education strictly as Month-Year (e.g. "Sept-2026", "May-2023", or year "2020"). If an experience or education is currently ongoing or present, set endDate to null or "Present".
                    
                    {format}
                    
                    RESUME TEXT:
                    {resumeText}
                    """;

            PromptTemplate template = new PromptTemplate(promptString);
            template.add("format", converter.getFormat());
            template.add("resumeText", resumeText);

            String aiResponse = chatClient.prompt(template.create())
                    .call()
                    .content();

            logger.info("[STEP 4/4] AI responded successfully. Parsing JSON and updating profile...");
            parsedData = converter.convert(aiResponse);
            
            if (parsedData == null) {
                throw new BadRequestException("AI returned invalid or empty JSON.");
            }

        } catch (Exception e) {
            logger.error("Background Job Failed: Failed to parse resume for Candidate ID: {}", candidateId, e);
            failureReason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }

        // STEP 2: Commit final status (COMPLETED or FAILED) and merged data in an isolated transaction
        final ParsedResumeResponse finalParsedData = parsedData;
        final String finalFailureReason = failureReason;

        boolean success = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            var profileOpt = candidateProfileRepository.findById(candidateId);
            if (profileOpt.isEmpty()) {
                return false;
            }
            CandidateProfile profile = profileOpt.get();

            if (finalParsedData != null) {
                mergeProfileData(profile, finalParsedData);
                profile.setParsingStatus(ParsingStatus.COMPLETED);
                profile.setParsingFailureReason(null);
                logger.info("Background Job Completed: Successfully parsed resume for Candidate ID: {}", candidateId);
            } else {
                profile.setParsingStatus(ParsingStatus.FAILED);
                profile.setParsingFailureReason(finalFailureReason);
            }

            candidateProfileRepository.save(profile);
            return finalParsedData != null;
        }));

        // STEP 3: Evict caches so UI polling gets fresh COMPLETED status & profile data
        evictCandidateCaches(candidateId);

        // STEP 4: Regenerate recommendations if completed
        if (success) {
            try {
                recommendationService.generateRecommendationsForCandidate(candidateId);
                logger.info("Triggered recommendation regeneration for candidate {} after resume parse", candidateId);
            } catch (Exception e) {
                logger.warn("Failed to trigger recommendation regeneration after parse for candidate {}: {}", candidateId, e.getMessage());
            }
        }
    }
    
    private void mergeProfileData(CandidateProfile profile, ParsedResumeResponse parsedData) {
        // Smart Merge: Only overwrite if empty
        if (profile.getFirstName() == null && parsedData.getFirstName() != null) profile.setFirstName(parsedData.getFirstName());
        if (profile.getLastName() == null && parsedData.getLastName() != null) profile.setLastName(parsedData.getLastName());
        if (profile.getPhone() == null && parsedData.getPhone() != null) profile.setPhone(parsedData.getPhone());
        if (profile.getLanguages() == null && parsedData.getLanguages() != null) profile.setLanguages(parsedData.getLanguages());
        if (profile.getPortfolioUrl() == null && parsedData.getLinkedin() != null) profile.setPortfolioUrl(parsedData.getLinkedin());
        
        // Always update AI extracted skills
        if (parsedData.getSkills() != null && !parsedData.getSkills().isEmpty()) {
            profile.setSkills(parsedData.getSkills());
        }
        
        // Clear collections - orphanRemoval = true automatically deletes old orphans on save
        profile.getEducations().clear();
        profile.getExperiences().clear();
        profile.getCertifications().clear();
        
        if (parsedData.getEducation() != null) {
            for (ParsedResumeResponse.ParsedEducation edu : parsedData.getEducation()) {
                Education e = new Education();
                e.setCandidateProfile(profile);
                e.setDegree(edu.getDegree() != null ? edu.getDegree() : "Unknown");
                e.setInstitution(edu.getInstitution() != null ? edu.getInstitution() : "Unknown");
                e.setDescription(edu.getDescription());
                e.setStartDate(DateUtils.formatMonthYear(edu.getStartDate()));
                e.setEndDate(DateUtils.formatMonthYear(edu.getEndDate()));
                profile.getEducations().add(e);
            }
        }
        
        if (parsedData.getExperience() != null) {
            for (ParsedResumeResponse.ParsedExperience exp : parsedData.getExperience()) {
                Experience e = new Experience();
                e.setCandidateProfile(profile);
                e.setJobTitle(exp.getJobTitle() != null ? exp.getJobTitle() : "Unknown");
                e.setCompany(exp.getCompany() != null ? exp.getCompany() : "Unknown");
                e.setDescription(exp.getDescription());
                e.setStartDate(DateUtils.formatMonthYear(exp.getStartDate()));
                e.setEndDate(DateUtils.formatMonthYear(exp.getEndDate()));
                profile.getExperiences().add(e);
            }
        }
        
        if (parsedData.getCertifications() != null) {
            for (ParsedResumeResponse.ParsedCertification cert : parsedData.getCertifications()) {
                Certification c = new Certification();
                c.setCandidateProfile(profile);
                c.setName(cert.getName() != null ? cert.getName() : "Unknown");
                c.setIssuingOrganization(cert.getIssuingOrganization() != null ? cert.getIssuingOrganization() : "Unknown");
                c.setIssueDate(cert.getIssueDate());
                profile.getCertifications().add(c);
            }
        }

        // Re-calculate completion score (mock logic for now, usually out of 100)
        profile.setProfileCompletionScore(calculateCompletionScore(profile));
    }
    
    private int calculateCompletionScore(CandidateProfile p) {
        int score = 20; // Base score
        if (p.getFirstName() != null) score += 10;
        if (p.getSkills() != null && !p.getSkills().isEmpty()) score += 20;
        if (p.getEducations() != null && !p.getEducations().isEmpty()) score += 20;
        if (p.getExperiences() != null && !p.getExperiences().isEmpty()) score += 30;
        return Math.min(score, 100);
    }

    /**
     * Programmatically evicts the candidate-related cache entries after a background job completes.
     * Ensures stale PENDING or PROCESSING status and old profile data are cleared from Redis immediately.
     *
     * @param candidateId the ID of the candidate whose caches should be evicted
     */
    private void evictCandidateCaches(Long candidateId) {
        String[] cacheNames = {"candidateProfiles", "dashboardMetrics", "recommendations"};
        String[] keys = {
            String.valueOf(candidateId),
            "candidate_" + candidateId,
            "jobs_for_candidate_" + candidateId
        };
        for (int i = 0; i < cacheNames.length; i++) {
            try {
                var cache = cacheManager.getCache(cacheNames[i]);
                if (cache != null) {
                    cache.evict(keys[i]);
                    cache.evict(candidateId);
                    cache.evict("summary_" + candidateId);
                    logger.debug("ResumeParsingService evicted cache {}::{}", cacheNames[i], keys[i]);
                }
            } catch (Exception e) {
                logger.warn("ResumeParsingService failed to evict cache {}::{}: {}", cacheNames[i], keys[i], e.getMessage());
            }
        }
    }
}
