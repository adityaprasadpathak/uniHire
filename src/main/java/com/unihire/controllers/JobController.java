package com.unihire.controllers;

import com.unihire.dto.ChangeJobStatusRequest;
import com.unihire.dto.JobRequestDto;
import com.unihire.dto.JobResponseDto;
import com.unihire.models.JobStatus;
import com.unihire.models.User;
import com.unihire.services.job.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;

/**
 * Controller providing REST API endpoints for managing job postings by employers.
 * <p>
 * Allows authenticated employers to create, retrieve, update, duplicate, and change status
 * of their job listings, as well as trigger job search index resynchronization.
 * </p>
 */
@Tag(name = "Employer Jobs", description = "Job posting management, drafting, duplication, and search index sync")
@RestController
@RequestMapping("/api/employer/jobs")
public class JobController {

    private final JobService jobService;

    /**
     * Constructs a new {@code JobController} with the specified {@link JobService}.
     *
     * @param jobService service managing job lifecycle and operations
     */
    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Creates a new job posting for the authenticated employer.
     *
     * @param user    the authenticated employer user context
     * @param request the payload containing job creation details (title, description, qualifications, etc.)
     * @return a {@link ResponseEntity} containing the created {@link JobResponseDto}
     */
    @PostMapping
    public ResponseEntity<JobResponseDto> createJob(
            @AuthenticationPrincipal User user,
            @RequestBody JobRequestDto request) {
        return ResponseEntity.ok(jobService.createJob(user.getId(), request));
    }

    /**
     * Retrieves all job postings created by the authenticated employer.
     *
     * @param user the authenticated employer user context
     * @return a {@link ResponseEntity} containing a list of {@link JobResponseDto} objects
     */
    @GetMapping
    public ResponseEntity<List<JobResponseDto>> getJobs(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(jobService.getJobsByEmployer(user.getId()));
    }

    /**
     * Retrieves detailed information for a specific job posting owned by the authenticated employer.
     *
     * @param id   the unique identifier of the job posting
     * @param user the authenticated employer user context
     * @return a {@link ResponseEntity} containing the {@link JobResponseDto}
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobResponseDto> getJobDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(jobService.getJobDetailsForEmployer(id, user.getId()));
    }

    /**
     * Updates an existing job posting owned by the authenticated employer.
     *
     * @param id      the unique identifier of the job posting to update
     * @param user    the authenticated employer user context
     * @param request the payload containing updated job fields
     * @return a {@link ResponseEntity} containing the updated {@link JobResponseDto}
     */
    @PutMapping("/{id}")
    public ResponseEntity<JobResponseDto> updateJob(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @RequestBody JobRequestDto request) {
        return ResponseEntity.ok(jobService.updateJob(id, user.getId(), request));
    }

    /**
     * Duplicates an existing job posting for the authenticated employer.
     *
     * @param id   the unique identifier of the job posting to duplicate
     * @param user the authenticated employer user context
     * @return a {@link ResponseEntity} containing the newly created duplicate {@link JobResponseDto}
     */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<JobResponseDto> duplicateJob(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(jobService.duplicateJob(id, user.getId()));
    }

    /**
     * Changes the lifecycle status of a job posting owned by the authenticated employer.
     *
     * @param id      the unique identifier of the job posting
     * @param user    the authenticated employer user context
     * @param request the payload containing the new status string (e.g., DRAFT, PUBLISHED, CLOSED)
     * @return a {@link ResponseEntity} containing the updated {@link JobResponseDto}
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<JobResponseDto> changeStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @RequestBody ChangeJobStatusRequest request) {
        JobStatus newStatus = JobStatus.valueOf(request.getStatus().toUpperCase());
        return ResponseEntity.ok(jobService.changeJobStatus(id, user.getId(), newStatus));
    }

    /**
     * Triggers a bulk resynchronization of all jobs to the OpenSearch search index.
     *
     * @return a {@link ResponseEntity} containing a confirmation message string
     */
    @PostMapping("/resync-opensearch")
    public ResponseEntity<String> resyncOpenSearch() {
        jobService.resyncAllJobsToOpenSearch();
        return ResponseEntity.ok("Resync triggered successfully");
    }
}
