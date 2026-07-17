package com.unihire.services;

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
import com.unihire.elasticsearch.JobDocument;
import com.unihire.elasticsearch.JobSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JobService {

    private static final Logger logger = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final EmployerProfileRepository employerProfileRepository;
    private final JobSearchRepository jobSearchRepository;

    public JobService(JobRepository jobRepository, 
                      EmployerProfileRepository employerProfileRepository,
                      JobSearchRepository jobSearchRepository) {
        this.jobRepository = jobRepository;
        this.employerProfileRepository = employerProfileRepository;
        this.jobSearchRepository = jobSearchRepository;
    }

    @Transactional
    public JobResponseDto createJob(Long employerId, JobRequestDto request) {
        EmployerProfile employer = employerProfileRepository.findById(employerId)
                .orElseThrow(() -> new ResourceNotFoundException("Employer not found"));

        Job job = Job.builder()
                .employer(employer)
                .title(request.getTitle())
                .description(request.getDescription())
                .experience(request.getExperience())
                .salary(request.getSalary())
                .skills(request.getSkills())
                .location(request.getLocation())
                .department(request.getDepartment())
                .employmentType(request.getEmploymentType())
                .numberOfVacancies(request.getNumberOfVacancies())
                .benefits(request.getBenefits())
                .expiryDate(request.getExpiryDate())
                .status(JobStatus.DRAFT) // Jobs are created as DRAFT by default
                .build();

        Job savedJob = jobRepository.save(job);
        syncToElasticsearch(savedJob);
        return mapToDto(savedJob);
    }

    @Transactional
    public JobResponseDto updateJob(Long jobId, Long employerId, JobRequestDto request) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized to modify this job");
        }

        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setExperience(request.getExperience());
        job.setSalary(request.getSalary());
        job.setSkills(request.getSkills());
        job.setLocation(request.getLocation());
        job.setDepartment(request.getDepartment());
        job.setEmploymentType(request.getEmploymentType());
        job.setNumberOfVacancies(request.getNumberOfVacancies());
        job.setBenefits(request.getBenefits());
        job.setExpiryDate(request.getExpiryDate());

        Job savedJob = jobRepository.save(job);
        syncToElasticsearch(savedJob);
        return mapToDto(savedJob);
    }

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
                .salary(originalJob.getSalary())
                .skills(originalJob.getSkills())
                .location(originalJob.getLocation())
                .department(originalJob.getDepartment())
                .employmentType(originalJob.getEmploymentType())
                .numberOfVacancies(originalJob.getNumberOfVacancies())
                .benefits(originalJob.getBenefits())
                // Do not copy expiryDate, application stats, publishedAt, closedAt
                .status(JobStatus.DRAFT) // Always create duplicate in DRAFT status
                .build();

        Job savedDuplicatedJob = jobRepository.save(duplicatedJob);
        syncToElasticsearch(savedDuplicatedJob);
        return mapToDto(savedDuplicatedJob);
    }

    @Transactional
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
        syncToElasticsearch(savedJob);
        return mapToDto(savedJob);
    }

    public List<JobResponseDto> getJobsByEmployer(Long employerId) {
        return jobRepository.findByEmployerId(employerId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void resyncAllJobsToElasticsearch() {
        jobSearchRepository.deleteAll();
        List<Job> allJobs = jobRepository.findAll();
        for (Job job : allJobs) {
            syncToElasticsearch(job);
        }
        logger.info("Successfully resynced {} jobs to Elasticsearch on startup", allJobs.size());
    }

    private void syncToElasticsearch(Job job) {
        try {
            JobDocument document = JobDocument.builder()
                    .id(job.getId().toString())
                    .title(job.getTitle())
                    .description(job.getDescription())
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
        } catch (Exception e) {
            logger.error("Failed to sync job ID {} to Elasticsearch: {}", job.getId(), e.getMessage());
            throw new RuntimeException("Elasticsearch Sync Failed: " + e.getMessage(), e);
        }
    }

    private JobResponseDto mapToDto(Job job) {
        JobResponseDto dto = new JobResponseDto();
        dto.setId(job.getId());
        dto.setTitle(job.getTitle());
        dto.setDescription(job.getDescription());
        dto.setExperience(job.getExperience());
        dto.setSalary(job.getSalary());
        dto.setSkills(job.getSkills());
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
        return dto;
    }
}
