package com.unihire.services.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihire.dto.*;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.exceptions.UnauthorizedOperationException;
import com.unihire.models.*;
import com.unihire.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ResumeBuilderService {

    @Autowired
    private CandidateResumeProfileRepository profileRepository;

    @Autowired
    private ResumeVersionRepository versionRepository;

    @Autowired
    private ResumeParsingJobRepository parsingJobRepository;

    @Autowired
    private AtsScoringEngine atsScoringEngine;

    @Autowired
    private ResumeLocalizationEngine localizationEngine;

    @Autowired
    private ResumeGenerationService resumeGenerationService;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public ResumeBuilderResponseDto getCandidateResumeBuilderData(Long candidateId) {
        ResumeBuilderResponseDto response = new ResumeBuilderResponseDto();
        response.setCandidateId(candidateId);

        // Fetch precomputed composite profile
        profileRepository.findById(candidateId).ifPresent(profile -> {
            response.setCompositeProfileJson(profile.getCompositeProfileJson());
        });

        // Fetch saved versions using the shared mapper
        List<ResumeVersionDto> versions = versionRepository.findByCandidateProfileId(candidateId).stream()
                .map(this::mapVersionToDto)
                .collect(Collectors.toList());
        response.setSavedVersions(versions);

        // Fetch latest parsing job
        parsingJobRepository.findTopByCandidateProfileIdOrderByCreatedAtDesc(candidateId).ifPresent(job -> {
            ResumeParsingJobDto jobDto = new ResumeParsingJobDto();
            jobDto.setId(job.getId());
            jobDto.setOriginalFileName(job.getOriginalFileName());
            jobDto.setStatus(job.getStatus());
            jobDto.setErrorMessage(job.getErrorMessage());
            jobDto.setUpdatedAt(job.getUpdatedAt());
            response.setLatestParsingJob(jobDto);
        });

        // Mock latest ATS score for dashboard
        response.setLatestAtsScore(atsScoringEngine.calculateAtsScore("Sample resume", "Sample JD"));

        return response;
    }

    public ResumeParsingJobDto initiateResumeParsing(Long candidateId, MultipartFile file) {
        ResumeParsingJob job = new ResumeParsingJob();
        // Assume candidate profile exists
        CandidateProfile profile = new CandidateProfile();
        profile.setId(candidateId);
        job.setCandidateProfile(profile);
        job.setOriginalFileName(file.getOriginalFilename());
        job.setStatus(ResumeParsingStatus.UPLOADED);

        job = parsingJobRepository.save(job);

        // Trigger async pipeline here
        // simulateAsyncParsing(job.getId());

        ResumeParsingJobDto dto = new ResumeParsingJobDto();
        dto.setId(job.getId());
        dto.setOriginalFileName(job.getOriginalFileName());
        dto.setStatus(job.getStatus());
        dto.setUpdatedAt(job.getUpdatedAt());
        return dto;
    }

    // Creates a PROCESSING version and kicks off async generation
    public ResumeVersionDto initiateVersionGeneration(Long candidateId, TargetCustomizationRequest request) {
        CandidateProfile profileRef = new CandidateProfile();
        profileRef.setId(candidateId);

        String versionName = request.getTargetRole() != null ? request.getTargetRole() : request.getIndustry();
        if (versionName == null) versionName = "Resume";
        if (request.getCountry() != null) versionName = versionName + " - " + request.getCountry();

        ResumeVersion version = ResumeVersion.builder()
                .candidateProfile(profileRef)
                .versionName(versionName)
                .targetIndustry(request.getIndustry())
                .targetCountry(request.getCountry())
                .targetRole(request.getTargetRole())
                .resumeTemplate(request.getResumeTemplate() != null ? request.getResumeTemplate() : "ATS_MODERN")
                .resumeStyle(request.getResumeStyle() != null ? request.getResumeStyle() : "CHRONOLOGICAL")
                .generationStatus(GenerationStatus.PROCESSING)
                .build();

        version = versionRepository.save(version);
        final Long versionId = version.getId();

        // Fire-and-forget async
        generateVersionAsync(candidateId, versionId, request);

        return mapVersionToDto(version);
    }

    // Async generation — creates version with PROCESSING status then does background work
    @Async
    public void generateVersionAsync(Long candidateId, Long versionId, TargetCustomizationRequest request) {
        try {
            CandidateProfile profile = candidateProfileRepository.findById(candidateId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profile not found: " + candidateId));

            // Trigger AI generation
            ResumeVersionDataDto versionData = resumeGenerationService.generateVersionData(profile, request);

            // Run ATS scoring on the merged content
            String resumeText = buildResumeTextForAts(versionData);
            AtsScoreDto atsScore = atsScoringEngine.calculateAtsScore(resumeText, request.getJobDescription());
            versionData.setAtsScore(atsScore.getTotalScore());
            versionData.setAiFeedback(atsScore.getFeedback());

            // Save completed version
            ResumeVersion version = versionRepository.findById(versionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionId));
            version.setVersionDataJson(objectMapper.writeValueAsString(versionData));
            version.setGenerationStatus(GenerationStatus.COMPLETED);
            version.setAtsScore(atsScore.getTotalScore());
            version.setAiFeedback(atsScore.getFeedback());
            versionRepository.save(version);

        } catch (Exception e) {
            ResumeVersion version = versionRepository.findById(versionId).orElse(null);
            if (version != null) {
                version.setGenerationStatus(GenerationStatus.FAILED);
                version.setErrorMessage(e.getMessage());
                versionRepository.save(version);
            }
        }
    }

    // Get a specific version (with ownership check)
    public ResumeVersionDto getVersion(Long versionId, Long candidateId) {
        ResumeVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionId));
        if (!version.getCandidateProfile().getId().equals(candidateId)) {
            throw new UnauthorizedOperationException("Access denied to version: " + versionId);
        }
        return mapVersionToDto(version);
    }

    // Delete a version (with ownership check)
    public void deleteVersion(Long versionId, Long candidateId) {
        ResumeVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionId));
        if (!version.getCandidateProfile().getId().equals(candidateId)) {
            throw new UnauthorizedOperationException("Access denied to version: " + versionId);
        }
        versionRepository.delete(version);
    }

    private String buildResumeTextForAts(ResumeVersionDataDto data) {
        StringBuilder sb = new StringBuilder();
        if (data.getProfessionalSummary() != null) sb.append(data.getProfessionalSummary()).append(" ");
        if (data.getSkills() != null) sb.append(String.join(" ", data.getSkills())).append(" ");
        if (data.getExperiences() != null) {
            data.getExperiences().forEach(e -> {
                sb.append(e.getJobTitle()).append(" ").append(e.getCompany()).append(" ");
                if (e.getBullets() != null) sb.append(String.join(" ", e.getBullets())).append(" ");
            });
        }
        if (data.getKeywords() != null) sb.append(String.join(" ", data.getKeywords()));
        return sb.toString();
    }

    private ResumeVersionDto mapVersionToDto(ResumeVersion v) {
        ResumeVersionDto dto = new ResumeVersionDto();
        dto.setId(v.getId());
        dto.setVersionName(v.getVersionName());
        dto.setVersionDataJson(v.getVersionDataJson());
        dto.setTargetIndustry(v.getTargetIndustry());
        dto.setTargetCountry(v.getTargetCountry());
        dto.setTargetRole(v.getTargetRole());
        dto.setResumeTemplate(v.getResumeTemplate());
        dto.setAtsScore(v.getAtsScore());
        dto.setAiFeedback(v.getAiFeedback());
        dto.setGenerationStatus(v.getGenerationStatus() != null ? v.getGenerationStatus().name() : "PROCESSING");
        dto.setUpdatedAt(v.getUpdatedAt());
        dto.setCreatedAt(v.getCreatedAt());
        return dto;
    }
}
