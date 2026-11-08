package com.unihire.services.candidate;
import com.unihire.services.ai.AiFeatureService;

import com.unihire.dto.ApplicationRequestDto;
import com.unihire.dto.ApplicationResponseDto;
import com.unihire.exceptions.BadRequestException;
import com.unihire.exceptions.DuplicateApplicationException;
import com.unihire.exceptions.JobExpiredException;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.exceptions.UnauthorizedOperationException;
import com.unihire.models.ApplicationStatus;
import com.unihire.models.CandidateProfile;
import com.unihire.models.Job;
import com.unihire.models.JobApplication;
import com.unihire.models.JobStatus;
import com.unihire.repositories.CandidateProfileRepository;
import com.unihire.repositories.JobApplicationRepository;
import com.unihire.repositories.JobRepository;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final AiFeatureService aiFeatureService;
    private final com.unihire.repositories.ChatMessageRepository chatMessageRepository;
    private final CacheManager cacheManager;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ApplicationService.class);

    public ApplicationService(JobApplicationRepository applicationRepository,
                              JobRepository jobRepository,
                              CandidateProfileRepository candidateProfileRepository,
                              AiFeatureService aiFeatureService,
                              com.unihire.repositories.ChatMessageRepository chatMessageRepository,
                              CacheManager cacheManager) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.aiFeatureService = aiFeatureService;
        this.chatMessageRepository = chatMessageRepository;
        this.cacheManager = cacheManager;
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "dashboardMetrics", key = "'candidate_' + #candidateId", beforeInvocation = true),
        @CacheEvict(value = "recommendations", key = "'jobs_for_candidate_' + #candidateId", beforeInvocation = true)
    })
    public ApplicationResponseDto applyForJob(Long candidateId, Long jobId, ApplicationRequestDto request) {
        if (applicationRepository.existsByJobIdAndCandidateProfileId(jobId, candidateId)) {
            throw new DuplicateApplicationException("You have already applied for this job.");
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw new BadRequestException("Cannot apply to an unpublished job.");
        }
        
        if (job.getExpiryDate() != null && job.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new JobExpiredException("Cannot apply to an expired job.");
        }

        CandidateProfile candidate = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));

        JobApplication application = JobApplication.builder()
                .job(job)
                .candidateProfile(candidate)
                .coverLetter(request.getCoverLetter())
                .status(ApplicationStatus.APPLIED)
                .build();

        JobApplication savedApplication = applicationRepository.save(application);

        try {
            aiFeatureService.evaluateApplication(savedApplication);
        } catch (Exception e) {
            // Log warning but allow application to succeed
        }

        // Evict candidate's dashboard (recentApplications has changed) and recommendation cache
        // (the applied job must no longer appear in recommended jobs list)
        evictCache("dashboardMetrics", "candidate_" + candidateId);
        evictCache("recommendations", "jobs_for_candidate_" + candidateId);

        // Evict employer analytics so application count is fresh
        Long employerUserId = job.getEmployer().getUser().getId();
        evictCache("analytics", "employer_analytics_" + employerUserId);

        return mapToDto(savedApplication);
    }

    public List<ApplicationResponseDto> getCandidateApplications(Long candidateId) {
        return applicationRepository.findByCandidateProfileId(candidateId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public Set<Long> getAppliedJobIds(Long candidateId) {
        return applicationRepository.findAppliedJobIdsByCandidateProfileId(candidateId);
    }

    public List<ApplicationResponseDto> getJobApplications(Long employerId, Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized access to this job's applications");
        }

        java.util.Map<Long, Integer> unreadCountsMap = chatMessageRepository.countUnreadByReceiverGroupedByApplication(employerId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Long) row[1]).intValue()
                ));

        return applicationRepository.findByJobId(jobId).stream()
                .map(app -> {
                    ApplicationResponseDto dto = mapToDto(app);
                    dto.setUnreadMessageCount(unreadCountsMap.getOrDefault(app.getId(), 0));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public ApplicationResponseDto getApplicationDetails(Long employerId, Long applicationId) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        if (!application.getJob().getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized access to this application");
        }

        return mapToDto(application);
    }

    @Transactional
    public ApplicationResponseDto changeApplicationStatus(Long employerId, Long applicationId, ApplicationStatus newStatus) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        if (!application.getJob().getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized access to modify this application");
        }

        application.setStatus(newStatus);
        JobApplication updatedApplication = applicationRepository.save(application);

        // Evict employer analytics: hiring/shortlisting metrics have changed
        Long employerUserId = application.getJob().getEmployer().getUser().getId();
        evictCache("analytics", "employer_analytics_" + employerUserId);

        // Evict candidate dashboard so their application status refreshes
        Long candidateId = application.getCandidateProfile().getUser().getId();
        evictCache("dashboardMetrics", "candidate_" + candidateId);

        return mapToDto(updatedApplication);
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
        dto.setCandidateVerificationStatus(application.getCandidateProfile().getVerificationStatus());
        
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

    /**
     * Programmatically evicts a cache entry by cache name and key.
     * Used in methods where Spring AOP proxy caching is not available or where
     * the key cannot be expressed as a simple SpEL expression on the method parameters.
     *
     * @param cacheName the name of the cache (e.g., "analytics", "dashboardMetrics")
     * @param key       the cache key to evict
     */
    private void evictCache(String cacheName, String key) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                logger.debug("Evicted cache {}::{}", cacheName, key);
            }
        } catch (Exception e) {
            logger.warn("Failed to evict cache {}::{}: {}", cacheName, key, e.getMessage());
        }
    }
}
