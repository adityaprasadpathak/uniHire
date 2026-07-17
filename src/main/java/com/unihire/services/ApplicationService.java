package com.unihire.services;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final CandidateProfileRepository candidateProfileRepository;

    public ApplicationService(JobApplicationRepository applicationRepository,
                              JobRepository jobRepository,
                              CandidateProfileRepository candidateProfileRepository) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.candidateProfileRepository = candidateProfileRepository;
    }

    @Transactional
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
        return mapToDto(savedApplication);
    }

    public List<ApplicationResponseDto> getCandidateApplications(Long candidateId) {
        return applicationRepository.findByCandidateProfileId(candidateId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<ApplicationResponseDto> getJobApplications(Long employerId, Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getEmployer().getUser().getId().equals(employerId)) {
            throw new UnauthorizedOperationException("Unauthorized access to this job's applications");
        }

        return applicationRepository.findByJobId(jobId).stream()
                .map(this::mapToDto)
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
}
