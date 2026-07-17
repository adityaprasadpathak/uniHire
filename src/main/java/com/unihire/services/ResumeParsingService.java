package com.unihire.services;

import com.unihire.dto.ParsedResumeResponse;
import com.unihire.exceptions.BadRequestException;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.models.*;
import com.unihire.repositories.CandidateProfileRepository;
import com.unihire.repositories.CertificationRepository;
import com.unihire.repositories.EducationRepository;
import com.unihire.repositories.ExperienceRepository;
import com.unihire.services.extractor.DocumentTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

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

    public ResumeParsingService(CandidateProfileRepository candidateProfileRepository,
                                EducationRepository educationRepository,
                                ExperienceRepository experienceRepository,
                                CertificationRepository certificationRepository,
                                StorageService storageService,
                                DocumentTextExtractor textExtractor,
                                ChatClient.Builder chatClientBuilder) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.educationRepository = educationRepository;
        this.experienceRepository = experienceRepository;
        this.certificationRepository = certificationRepository;
        this.storageService = storageService;
        this.textExtractor = textExtractor;
        this.chatClient = chatClientBuilder.build();
    }

    @Transactional
    public void parseResume(Long candidateId, String filename) {
        logger.info("Background Job Started: Parsing resume for Candidate ID: {} from file: {}", candidateId, filename);

        CandidateProfile profile = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));

        profile.setParsingStatus(ParsingStatus.PROCESSING);
        candidateProfileRepository.save(profile);

        try {
            logger.info("[STEP 1/4] Downloading file from storage: {}", filename);
            // 1. Download file via SDK
            InputStream fileStream = storageService.getFileStream(filename);
            
            logger.info("[STEP 2/4] Extracting text from document using Apache Tika...");
            // 2. Extract Text using Tika
            String resumeText = textExtractor.extractText(fileStream, filename);
            
            if (resumeText == null || resumeText.trim().isEmpty()) {
                throw new BadRequestException("Resume contained no extractable text.");
            }

            logger.info("Successfully extracted {} characters of text.", resumeText.length());
            logger.info("[STEP 3/4] Sending text to AI for structured extraction (this may take 1-2 minutes depending on local hardware)...");

            // 3. Setup Spring AI BeanOutputConverter
            BeanOutputConverter<ParsedResumeResponse> converter = new BeanOutputConverter<>(ParsedResumeResponse.class);
            
            String promptString = """
                    Extract the candidate information from the following resume text.
                    Return ONLY valid JSON matching the exact schema requested.
                    Do not explain.
                    If a field is missing, use null.
                    If no skills exist, return [].
                    Never invent data.
                    Never guess dates. If a date is just a year (e.g., '2020'), return '2020-01-01'.
                    
                    {format}
                    
                    RESUME TEXT:
                    {resumeText}
                    """;

            PromptTemplate template = new PromptTemplate(promptString);
            template.add("format", converter.getFormat());
            template.add("resumeText", resumeText);

            // 4. Call LLM (Ollama locally)
            String aiResponse = chatClient.prompt(template.create())
                    .call()
                    .content();

            logger.info("[STEP 4/4] AI responded successfully. Parsing JSON and updating profile...");

            // 5. Parse JSON
            ParsedResumeResponse parsedData = converter.convert(aiResponse);
            
            if (parsedData == null) {
                throw new BadRequestException("AI returned invalid or empty JSON.");
            }

            // 6. Merge data into Profile (Smart Merge)
            mergeProfileData(profile, parsedData);

            profile.setParsingStatus(ParsingStatus.COMPLETED);
            profile.setParsingFailureReason(null);
            logger.info("Background Job Completed: Successfully parsed resume for Candidate ID: {}", candidateId);

        } catch (Exception e) {
            logger.error("Background Job Failed: Failed to parse resume for Candidate ID: {}", candidateId, e);
            profile.setParsingStatus(ParsingStatus.FAILED);
            profile.setParsingFailureReason(e.getMessage());
        }

        candidateProfileRepository.save(profile);
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
        
        // Re-calculate completion score (mock logic for now, usually out of 100)
        profile.setProfileCompletionScore(calculateCompletionScore(profile));

        // Delete old AI extracted collections and save new ones
        educationRepository.deleteAll(profile.getEducations());
        experienceRepository.deleteAll(profile.getExperiences());
        certificationRepository.deleteAll(profile.getCertifications());
        
        profile.getEducations().clear();
        profile.getExperiences().clear();
        profile.getCertifications().clear();
        
        if (parsedData.getEducation() != null) {
            for (ParsedResumeResponse.ParsedEducation edu : parsedData.getEducation()) {
                Education e = new Education();
                e.setCandidateProfile(profile);
                e.setDegree(edu.getDegree());
                e.setInstitution(edu.getInstitution());
                e.setDescription(edu.getDescription());
                e.setStartDate(parseDate(edu.getStartDate()));
                e.setEndDate(parseDate(edu.getEndDate()));
                profile.getEducations().add(e);
            }
        }
        
        if (parsedData.getExperience() != null) {
            for (ParsedResumeResponse.ParsedExperience exp : parsedData.getExperience()) {
                Experience e = new Experience();
                e.setCandidateProfile(profile);
                e.setJobTitle(exp.getJobTitle());
                e.setCompany(exp.getCompany());
                e.setDescription(exp.getDescription());
                e.setStartDate(parseDate(exp.getStartDate()));
                e.setEndDate(parseDate(exp.getEndDate()));
                profile.getExperiences().add(e);
            }
        }
        
        if (parsedData.getCertifications() != null) {
            for (ParsedResumeResponse.ParsedCertification cert : parsedData.getCertifications()) {
                Certification c = new Certification();
                c.setCandidateProfile(profile);
                c.setName(cert.getName());
                c.setIssuingOrganization(cert.getIssuingOrganization());
                c.setIssueDate(parseDate(cert.getIssueDate()));
                profile.getCertifications().add(c);
            }
        }
    }
    
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        try {
            // Very simple date parsing. A robust system would handle formats like "Jan 2020", "2020", etc.
            // But we prompt the AI to return YYYY-MM-DD
            if (dateStr.length() == 4) {
                return LocalDate.of(Integer.parseInt(dateStr), 1, 1);
            }
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
        } catch (DateTimeParseException | NumberFormatException e) {
            logger.warn("Could not parse date from AI response: {}", dateStr);
            return null;
        }
    }
    
    private int calculateCompletionScore(CandidateProfile p) {
        int score = 20; // Base score
        if (p.getFirstName() != null) score += 10;
        if (p.getSkills() != null && !p.getSkills().isEmpty()) score += 20;
        if (p.getEducations() != null && !p.getEducations().isEmpty()) score += 20;
        if (p.getExperiences() != null && !p.getExperiences().isEmpty()) score += 30;
        return Math.min(score, 100);
    }
}
