package com.unihire.services.job;
import com.unihire.services.search.OpenSearchAvailabilityService;
import com.unihire.services.search.SemanticSearchService;
import com.unihire.services.ai.RecommendationService;

import com.unihire.dto.JobRequestDto;
import com.unihire.dto.JobResponseDto;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.exceptions.UnauthorizedOperationException;
import com.unihire.models.EmployerProfile;
import com.unihire.models.Job;
import com.unihire.models.JobStatus;
import com.unihire.models.VerificationStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.unihire.repositories.EmployerProfileRepository;
import com.unihire.repositories.JobRepository;
import com.unihire.opensearch.JobDocument;
import com.unihire.opensearch.JobSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.core.env.Environment;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service class responsible for managing Job entities.
 * Handles job creation, updating, duplication, status changes, and deletion.
 * Also manages synchronization of job data with OpenSearch and PgVector for search capabilities.
 */
@Service
public class JobService {

    private static final Logger logger = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final EmployerProfileRepository employerProfileRepository;
    private final JobSearchRepository jobSearchRepository;
    private final OpenSearchAvailabilityService openSearchAvailabilityService;
    private final SemanticSearchService semanticSearchService;
    private final Environment environment;
    private final RecommendationService recommendationService;
    private final CacheManager cacheManager;

    public JobService(JobRepository jobRepository,
                      EmployerProfileRepository employerProfileRepository,
                      JobSearchRepository jobSearchRepository,
                      OpenSearchAvailabilityService openSearchAvailabilityService,
                      SemanticSearchService semanticSearchService,
                      Environment environment,
                      RecommendationService recommendationService,
                      CacheManager cacheManager) {
        this.jobRepository = jobRepository;
        this.employerProfileRepository = employerProfileRepository;
        this.jobSearchRepository = jobSearchRepository;
        this.openSearchAvailabilityService = openSearchAvailabilityService;
        this.semanticSearchService = semanticSearchService;
        this.environment = environment;
        this.recommendationService = recommendationService;
        this.cacheManager = cacheManager;
    }

    /**
     * Creates a new job posting for the specified employer.
     * The job is initially created in the DRAFT status.
     *
     * @param employerId the ID of the employer creating the job
     * @param request the data transfer object containing job details
     * @return the created job represented as a JobResponseDto
     * @throws ResourceNotFoundException if the employer profile is not found
     */
    @Transactional
    @CacheEvict(value = "analytics", key = "'employer_analytics_' + #employerId", beforeInvocation = true)
    public JobResponseDto createJob(Long employerId, JobRequestDto request) {
        EmployerProfile employer = employerProfileRepository.findById(employerId)
                .orElseThrow(() -> new ResourceNotFoundException("Employer not found"));

        Job job = Job.builder()
                .employer(employer)
                .title(request.getTitle())
                .description(request.getDescription())
                .experience(request.getExperience())
                .minExperience(request.getMinExperience())
                .maxExperience(request.getMaxExperience())
                .salary(request.getSalary())
                .skills(request.getSkills())
                .preferredSkills(request.getPreferredSkills())
                .location(request.getLocation())
                .department(request.getDepartment())
                .employmentType(request.getEmploymentType())
                .numberOfVacancies(request.getNumberOfVacancies())
                .benefits(request.getBenefits())
                .expiryDate(request.getExpiryDate())
                .jobLevel(request.getJobLevel())
                .workMode(request.getWorkMode())
                .openings(request.getOpenings())
                .education(request.getEducation())
                .certifications(request.getCertifications())
                .languages(request.getLanguages())
                .responsibilities(request.getResponsibilities())
                .qualifications(request.getQualifications())
                .workingDays(request.getWorkingDays())
                .shiftTiming(request.getShiftTiming())
                .travelRequirement(request.getTravelRequirement())
                .noticePeriod(request.getNoticePeriod())
                .employmentCategory(request.getEmploymentCategory())
                .industry(request.getIndustry())
                .incentives(request.getIncentives())
                .hiringManager(request.getHiringManager())
                .recruiter(request.getRecruiter())
                .interviewProcess(request.getInterviewProcess())
                .screeningQuestions(request.getScreeningQuestions())
                .status(JobStatus.DRAFT) // Jobs are created as DRAFT by default
                .build();

        Job savedJob = jobRepository.save(job);
        syncToOpenSearch(savedJob);
        return mapToDto(savedJob);
    }

    /**
     * Updates an existing job posting.
     * Only the employer who created the job can update it.
     *
     * @param jobId the ID of the job to update
     * @param employerId the ID of the employer requesting the update
     * @param request the data transfer object containing updated job details
     * @return the updated job represented as a JobResponseDto
     * @throws ResourceNotFoundException if the job is not found
     * @throws UnauthorizedOperationException if the employer is not the owner of the job
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "publicJobs", key = "#jobId", beforeInvocation = true),
        @CacheEvict(value = "recommendations", key = "'candidates_for_job_' + #jobId", beforeInvocation = true)
    })
    public JobResponseDto updateJob(Long jobId, Long employerId, JobRequestDto request) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized to modify this job");
        }

        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setExperience(request.getExperience());
        job.setMinExperience(request.getMinExperience());
        job.setMaxExperience(request.getMaxExperience());
        job.setSalary(request.getSalary());
        job.setSkills(request.getSkills());
        job.setPreferredSkills(request.getPreferredSkills());
        job.setLocation(request.getLocation());
        job.setDepartment(request.getDepartment());
        job.setEmploymentType(request.getEmploymentType());
        job.setNumberOfVacancies(request.getNumberOfVacancies());
        job.setBenefits(request.getBenefits());
        job.setExpiryDate(request.getExpiryDate());

        job.setJobLevel(request.getJobLevel());
        job.setWorkMode(request.getWorkMode());
        job.setOpenings(request.getOpenings());
        job.setEducation(request.getEducation());
        job.setCertifications(request.getCertifications());
        job.setLanguages(request.getLanguages());
        job.setResponsibilities(request.getResponsibilities());
        job.setQualifications(request.getQualifications());
        job.setWorkingDays(request.getWorkingDays());
        job.setShiftTiming(request.getShiftTiming());
        job.setTravelRequirement(request.getTravelRequirement());
        job.setNoticePeriod(request.getNoticePeriod());
        job.setEmploymentCategory(request.getEmploymentCategory());
        job.setIndustry(request.getIndustry());
        job.setIncentives(request.getIncentives());
        job.setHiringManager(request.getHiringManager());
        job.setRecruiter(request.getRecruiter());
        job.setInterviewProcess(request.getInterviewProcess());
        job.setScreeningQuestions(request.getScreeningQuestions());

        Job savedJob = jobRepository.save(job);
        syncToOpenSearch(savedJob);
        return mapToDto(savedJob);
    }

    /**
     * Duplicates an existing job posting, creating a new job in DRAFT status.
     * Only the employer who created the original job can duplicate it.
     * The duplicated job will have " (Copy)" appended to its title.
     *
     * @param jobId the ID of the original job to duplicate
     * @param employerId the ID of the employer requesting the duplication
     * @return the newly duplicated job represented as a JobResponseDto
     * @throws ResourceNotFoundException if the original job is not found
     * @throws UnauthorizedOperationException if the employer is not the owner of the original job
     */
    @Transactional
    public JobResponseDto duplicateJob(Long jobId, Long employerId) {
        Job originalJob = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!originalJob.getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized to duplicate this job");
        }

        Job duplicatedJob = Job.builder()
                .employer(originalJob.getEmployer())
                .title(originalJob.getTitle() + " (Copy)")
                .description(originalJob.getDescription())
                .experience(originalJob.getExperience())
                .minExperience(originalJob.getMinExperience())
                .maxExperience(originalJob.getMaxExperience())
                .salary(originalJob.getSalary())
                .skills(originalJob.getSkills())
                .preferredSkills(originalJob.getPreferredSkills())
                .location(originalJob.getLocation())
                .department(originalJob.getDepartment())
                .employmentType(originalJob.getEmploymentType())
                .numberOfVacancies(originalJob.getNumberOfVacancies())
                .benefits(originalJob.getBenefits())
                .jobLevel(originalJob.getJobLevel())
                .workMode(originalJob.getWorkMode())
                .openings(originalJob.getOpenings())
                .education(originalJob.getEducation())
                .certifications(originalJob.getCertifications())
                .languages(originalJob.getLanguages())
                .responsibilities(originalJob.getResponsibilities())
                .qualifications(originalJob.getQualifications())
                .workingDays(originalJob.getWorkingDays())
                .shiftTiming(originalJob.getShiftTiming())
                .travelRequirement(originalJob.getTravelRequirement())
                .noticePeriod(originalJob.getNoticePeriod())
                .employmentCategory(originalJob.getEmploymentCategory())
                .industry(originalJob.getIndustry())
                .incentives(originalJob.getIncentives())
                .hiringManager(originalJob.getHiringManager())
                .recruiter(originalJob.getRecruiter())
                .interviewProcess(originalJob.getInterviewProcess())
                .screeningQuestions(originalJob.getScreeningQuestions())
                .status(JobStatus.DRAFT) // Always create duplicate in DRAFT status
                .build();

        Job savedDuplicatedJob = jobRepository.save(duplicatedJob);
        syncToOpenSearch(savedDuplicatedJob);
        return mapToDto(savedDuplicatedJob);
    }

    /**
     * Changes the status of an existing job posting.
     * Only the employer who created the job can change its status.
     * If changing to PUBLISHED, the employer must have a VERIFIED status.
     * It also sets the publishedAt or closedAt timestamps based on the new status,
     * and triggers recommendation generation when a job is published.
     *
     * @param jobId the ID of the job
     * @param employerId the ID of the employer requesting the status change
     * @param newStatus the new status to apply to the job
     * @return the updated job represented as a JobResponseDto
     * @throws ResourceNotFoundException if the job is not found
     * @throws UnauthorizedOperationException if the employer is not the owner or if they attempt to publish without verification
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "publicJobs", key = "#jobId", beforeInvocation = true),
        @CacheEvict(value = "recommendations", key = "'candidates_for_job_' + #jobId", beforeInvocation = true)
    })
    public JobResponseDto changeJobStatus(Long jobId, Long employerId, JobStatus newStatus) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized to modify this job");
        }

        if (newStatus == JobStatus.PUBLISHED && job.getEmployer().getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new UnauthorizedOperationException("Action forbidden: Employer account is not verified yet.");
        }

        job.setStatus(newStatus);
        
        if (newStatus == JobStatus.PUBLISHED && job.getPublishedAt() == null) {
            job.setPublishedAt(LocalDateTime.now());
        } else if (newStatus == JobStatus.CLOSED) {
            job.setClosedAt(LocalDateTime.now());
        }

        Job savedJob = jobRepository.save(job);
        syncToOpenSearch(savedJob);
        // Evict employer analytics so dashboard reflects new status immediately
        evictEmployerAnalyticsCache(savedJob.getEmployer().getUser().getId());
        if (newStatus == JobStatus.PUBLISHED) {
            recommendationService.generateRecommendationsForJob(savedJob.getId());
        }
        return mapToDto(savedJob);
    }

    /**
     * Retrieves a list of all jobs associated with a specific employer.
     *
     * @param employerId the ID of the employer
     * @return a list of JobResponseDto objects representing the employer's jobs
     */
    public List<JobResponseDto> getJobsByEmployer(Long employerId) {
        return jobRepository.findByEmployerId(employerId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves detailed information about a specific job.
     * Only the employer who created the job can access its details via this method.
     *
     * @param jobId the ID of the job to retrieve
     * @param employerId the ID of the employer requesting the job details
     * @return the job details represented as a JobResponseDto
     * @throws ResourceNotFoundException if the job is not found
     * @throws UnauthorizedOperationException if the employer is not the owner of the job
     */
    @Transactional(readOnly = true)
    public JobResponseDto getJobDetailsForEmployer(Long jobId, Long employerId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized to access this job");
        }

        return mapToDto(job);
    }

    /**
     * Listens for the ApplicationReadyEvent and resynchronizes all existing jobs
     * to the OpenSearch index upon startup.
     * This is only executed if the application is running with the 'local' active profile.
     * It clears the existing OpenSearch index and re-indexes all jobs.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void resyncAllJobsToOpenSearch() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (activeProfiles.contains("prod") || !activeProfiles.contains("local")) {
            logger.info("Skipping job startup index resync: environment is not local development.");
            return;
        }
        boolean syncOnStartup = environment.getProperty("spring.opensearch.sync-on-startup", Boolean.class, false);
        if (!syncOnStartup) {
            logger.info("Skipping job startup index resync: 'spring.opensearch.sync-on-startup' is disabled (default).");
            return;
        }
        boolean osAvailable = openSearchAvailabilityService.isAvailable();
        if (osAvailable) {
            try {
                jobSearchRepository.deleteAll();
                logger.info("Cleared existing job index in OpenSearch.");
            } catch (Exception e) {
                logger.error("Failed to clear OpenSearch jobs: {}", e.getMessage());
            }
        }
        List<Job> allJobs = jobRepository.findAllEager();
        for (Job job : allJobs) {
            syncToOpenSearch(job);
        }
        logger.info("Successfully synced {} jobs on startup (OpenSearch available: {})", allJobs.size(), osAvailable);
    }

    /**
     * Synchronizes a single job entity to both PgVector (semantic search) and OpenSearch.
     * Jobs with a DRAFT status are removed from the search indexes.
     * It builds a rich description incorporating various job details for improved OpenSearch indexing.
     *
     * @param job the job entity to synchronize
     */
    public void syncToOpenSearch(Job job) {
        if (job.getStatus() == JobStatus.DRAFT) {
            // Draft jobs should not be in OpenSearch or PgVector search indexes
            if (openSearchAvailabilityService.isAvailable()) {
                try {
                    jobSearchRepository.deleteById(job.getId().toString());
                } catch (Exception e) {
                    // Ignore if not present
                }
            }
            try {
                semanticSearchService.deleteJob(job.getId());
            } catch (Exception e) {
                // Ignore if not present
            }
            return;
        }

        // 1. Sync to PgVector (always)
        try {
            semanticSearchService.indexJob(job);
        } catch (Exception e) {
            logger.error("Failed to index job ID {} in PgVector: {}", job.getId(), e.getMessage());
        }

        // 2. Sync to OpenSearch if available
        if (openSearchAvailabilityService.isAvailable()) {
            try {
                // Build a rich description for OpenSearch containing all new parameters to ensure full searchability
                StringBuilder richDescription = new StringBuilder();
                richDescription.append(job.getDescription() != null ? job.getDescription() : "").append("\n\n");
                
                if (job.getResponsibilities() != null && !job.getResponsibilities().isEmpty()) {
                    richDescription.append("Responsibilities:\n").append(job.getResponsibilities()).append("\n");
                }
                if (job.getQualifications() != null && !job.getQualifications().isEmpty()) {
                    richDescription.append("Qualifications:\n").append(job.getQualifications()).append("\n");
                }
                if (job.getPreferredSkills() != null && !job.getPreferredSkills().isEmpty()) {
                    richDescription.append("Preferred Skills: ").append(job.getPreferredSkills()).append("\n");
                }
                if (job.getCertifications() != null && !job.getCertifications().isEmpty()) {
                    richDescription.append("Certifications: ").append(job.getCertifications()).append("\n");
                }
                if (job.getJobLevel() != null) {
                    richDescription.append("Job Level: ").append(job.getJobLevel()).append("\n");
                }
                if (job.getWorkMode() != null) {
                    richDescription.append("Work Mode: ").append(job.getWorkMode()).append("\n");
                }
                if (job.getEducation() != null) {
                    richDescription.append("Education: ").append(job.getEducation()).append("\n");
                }
                if (job.getLanguages() != null) {
                    richDescription.append("Languages: ").append(job.getLanguages()).append("\n");
                }

                JobDocument document = JobDocument.builder()
                        .id(job.getId().toString())
                        .title(job.getTitle())
                        .description(richDescription.toString())
                        .experience(job.getExperience())
                        .salary(job.getSalary())
                        .skills(job.getSkills())
                        .location(job.getLocation())
                        .department(job.getDepartment())
                        .employmentType(job.getEmploymentType())
                        .status(job.getStatus().name())
                        .expiryDate(job.getExpiryDate())
                        .publishedAt(job.getPublishedAt())
                        .companyName(job.getEmployer().getCompanyName())
                        .employerId(job.getEmployer().getUser().getId())
                        .build();
                jobSearchRepository.save(document);
                logger.info("Successfully synced job ID {} to OpenSearch", job.getId());
            } catch (Exception e) {
                logger.error("Failed to sync job ID {} to OpenSearch: {}", job.getId(), e.getMessage());
                openSearchAvailabilityService.setUnavailable();
            }
        }
    }

    /**
     * Maps a Job entity object to a JobResponseDto data transfer object.
     *
     * @param job the Job entity to map
     * @return the mapped JobResponseDto
     */
    private JobResponseDto mapToDto(Job job) {
        JobResponseDto dto = new JobResponseDto();
        dto.setId(job.getId());
        dto.setTitle(job.getTitle());
        dto.setDescription(job.getDescription());
        dto.setExperience(job.getExperience());
        dto.setMinExperience(job.getMinExperience());
        dto.setMaxExperience(job.getMaxExperience());
        dto.setSalary(job.getSalary());
        dto.setSkills(job.getSkills());
        dto.setPreferredSkills(job.getPreferredSkills());
        dto.setLocation(job.getLocation());
        dto.setDepartment(job.getDepartment());
        dto.setEmploymentType(job.getEmploymentType());
        dto.setNumberOfVacancies(job.getNumberOfVacancies());
        dto.setBenefits(job.getBenefits());
        dto.setStatus(job.getStatus().name());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setUpdatedAt(job.getUpdatedAt());
        dto.setPublishedAt(job.getPublishedAt());
        dto.setClosedAt(job.getClosedAt());
        dto.setExpiryDate(job.getExpiryDate());

        dto.setJobLevel(job.getJobLevel());
        dto.setWorkMode(job.getWorkMode());
        dto.setOpenings(job.getOpenings());
        dto.setEducation(job.getEducation());
        dto.setCertifications(job.getCertifications());
        dto.setLanguages(job.getLanguages());
        dto.setResponsibilities(job.getResponsibilities());
        dto.setQualifications(job.getQualifications());
        dto.setWorkingDays(job.getWorkingDays());
        dto.setShiftTiming(job.getShiftTiming());
        dto.setTravelRequirement(job.getTravelRequirement());
        dto.setNoticePeriod(job.getNoticePeriod());
        dto.setEmploymentCategory(job.getEmploymentCategory());
        dto.setIndustry(job.getIndustry());
        dto.setIncentives(job.getIncentives());
        dto.setHiringManager(job.getHiringManager());
        dto.setRecruiter(job.getRecruiter());
        dto.setInterviewProcess(job.getInterviewProcess());
        dto.setScreeningQuestions(job.getScreeningQuestions());
        return dto;
    }

    /**
     * Deletes a job posting from the relational database and search indexes.
     * Only jobs in the DRAFT status can be deleted.
     * Only the employer who created the job can delete it.
     *
     * @param jobId the ID of the job to delete
     * @param employerId the ID of the employer requesting the deletion
     * @throws ResourceNotFoundException if the job is not found
     * @throws UnauthorizedOperationException if the employer is not the owner of the job
     * @throws IllegalStateException if the job is not in DRAFT status
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "publicJobs", key = "#jobId", beforeInvocation = true),
        @CacheEvict(value = "recommendations", key = "'candidates_for_job_' + #jobId", beforeInvocation = true)
    })
    public void deleteJob(Long jobId, Long employerId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized to delete this job");
        }

        if (job.getStatus() != com.unihire.models.JobStatus.DRAFT) {
            throw new IllegalStateException("Only jobs in DRAFT status can be deleted");
        }

        // 1. Delete from OpenSearch if available
        if (openSearchAvailabilityService.isAvailable()) {
            try {
                jobSearchRepository.deleteById(jobId.toString());
                logger.info("Successfully deleted job {} from OpenSearch", jobId);
            } catch (Exception e) {
                logger.error("Failed to delete job ID {} from OpenSearch: {}", jobId, e.getMessage());
            }
        }

        // 2. Delete from PgVector
        try {
            semanticSearchService.deleteJob(jobId);
        } catch (Exception e) {
            logger.error("Failed to delete job ID {} from PgVector: {}", jobId, e.getMessage());
        }

        // 3. Delete from relational DB
        jobRepository.delete(job);
        // Evict employer analytics â€” job count has changed
        evictEmployerAnalyticsCache(job.getEmployer().getUser().getId());
        logger.info("Successfully deleted job {} from JPA database", jobId);
    }

    /**
     * Programmatically evicts the analytics cache for a given employer.
     * Used when job status changes affect aggregated analytics (active job count, etc.).
     *
     * @param employerId the ID of the employer whose analytics cache should be evicted
     */
    private void evictEmployerAnalyticsCache(Long employerId) {
        try {
            var cache = cacheManager.getCache("analytics");
            if (cache != null) {
                cache.evict("employer_analytics_" + employerId);
                logger.debug("Evicted analytics cache for employer {}", employerId);
            }
        } catch (Exception e) {
            logger.warn("Failed to evict analytics cache for employer {}: {}", employerId, e.getMessage());
        }
    }
}
