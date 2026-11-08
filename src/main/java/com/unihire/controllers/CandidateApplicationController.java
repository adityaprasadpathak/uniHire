package com.unihire.controllers;

import com.unihire.dto.ApplicationRequestDto;
import com.unihire.dto.ApplicationResponseDto;
import com.unihire.models.User;
import com.unihire.services.candidate.ApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for handling candidate job application operations.
 * Provides endpoints for candidates to apply for job postings and view their application history.
 */
@Tag(name = "Candidate Applications", description = "Job applications submission and status tracking for candidates")
@RestController
@RequestMapping("/api/candidate")
public class CandidateApplicationController {

    private final ApplicationService applicationService;

    /**
     * Constructs a new {@code CandidateApplicationController} with the required {@link ApplicationService}.
     *
     * @param applicationService service managing job applications and candidate submissions
     */
    public CandidateApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * Submits an application for a specific job posting on behalf of the authenticated candidate.
     *
     * @param jobId   unique ID of the job posting to apply for
     * @param user    authenticated candidate user details from security context
     * @param request application payload containing optional resume, cover letter, and application answers
     * @return {@link ResponseEntity} containing the created {@link ApplicationResponseDto} on success,
     *         or an HTTP 409 Conflict status with error details if the candidate already applied or the job is inactive
     */
    @PostMapping("/jobs/{jobId}/apply")
    public ResponseEntity<?> applyForJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user,
            @RequestBody ApplicationRequestDto request) {
        try {
            ApplicationResponseDto response = applicationService.applyForJob(user.getId(), jobId, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // Returning 409 Conflict if they already applied or if job is expired/unpublished
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Retrieves all job applications submitted by the authenticated candidate.
     *
     * @param user authenticated candidate user details from security context
     * @return {@link ResponseEntity} containing a list of {@link ApplicationResponseDto} instances representing candidate applications
     */
    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponseDto>> getMyApplications(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(applicationService.getCandidateApplications(user.getId()));
    }

    /**
     * Lightweight endpoint returning just the set of job IDs the candidate has applied for.
     * Used by dashboard widgets to prevent fetching heavy full application histories with AI fit analysis.
     *
     * @param user authenticated candidate user
     * @return set of applied job IDs
     */
    @GetMapping("/applications/applied-job-ids")
    public ResponseEntity<Set<Long>> getAppliedJobIds(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(applicationService.getAppliedJobIds(user.getId()));
    }
}
