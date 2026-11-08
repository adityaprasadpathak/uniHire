package com.unihire.controllers;

import com.unihire.dto.EmployerProfileDto;
import com.unihire.dto.UpdateEmployerProfileRequest;
import com.unihire.dto.ApplicationResponseDto;
import com.unihire.dto.CandidateSummaryDto;
import com.unihire.models.User;
import com.unihire.services.candidate.ApplicationService;
import com.unihire.services.candidate.CandidateService;
import com.unihire.services.employer.EmployerService;
import com.unihire.services.job.JobService;
import com.unihire.services.search.SemanticSearchService;
import com.unihire.dto.CandidateProfileDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import java.util.Map;

/**
 * Controller providing REST API endpoints for employer operations.
 * <p>
 * Handles employer profile management, candidate search and profile viewing,
 * job deletion, and application detail retrieval for authenticated employers.
 * </p>
 */
@Tag(name = "Employer Profile", description = "Employer organization profile management and verification status")
@RestController
@RequestMapping("/api/employer")
public class EmployerController {

    private final EmployerService employerService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final SemanticSearchService semanticSearchService;
    private final CandidateService candidateService;

    /**
     * Constructs a new {@code EmployerController} with required service dependencies.
     *
     * @param employerService       service for employer profile operations
     * @param jobService            service for job management operations
     * @param applicationService    service for job application processing
     * @param semanticSearchService service for candidate semantic search capabilities
     * @param candidateService      service for candidate profile operations
     */
    public EmployerController(EmployerService employerService, JobService jobService, ApplicationService applicationService, SemanticSearchService semanticSearchService, CandidateService candidateService) {
        this.employerService = employerService;
        this.jobService = jobService;
        this.applicationService = applicationService;
        this.semanticSearchService = semanticSearchService;
        this.candidateService = candidateService;
    }

    /**
     * Retrieves the profile details of the currently authenticated employer.
     *
     * @param user the authenticated employer user context
     * @return a {@link ResponseEntity} containing the {@link EmployerProfileDto}
     */
    @GetMapping("/profile")
    public ResponseEntity<EmployerProfileDto> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(employerService.getProfile(user.getId()));
    }

    /**
     * Retrieves detailed information for a specific job application submitted to the employer.
     *
     * @param id   the unique identifier of the job application
     * @param user the authenticated employer user context
     * @return a {@link ResponseEntity} containing the {@link ApplicationResponseDto}
     */
    @GetMapping("/applications/{id}")
    public ResponseEntity<ApplicationResponseDto> getApplicationDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(applicationService.getApplicationDetails(user.getId(), id));
    }

    /**
     * Searches for candidate profiles using semantic vector search.
     *
     * @param query the search query describing desired candidate qualifications or skills
     * @param topK  the maximum number of top candidate results to return (default is 10)
     * @return a {@link ResponseEntity} containing a list of matching {@link CandidateSummaryDto} objects
     */
    @GetMapping("/candidates/search")
    public ResponseEntity<List<CandidateSummaryDto>> searchCandidates(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK) {
        return ResponseEntity.ok(semanticSearchService.searchCandidates(query, topK));
    }

    /**
     * Retrieves the full profile of a candidate by their unique identifier.
     *
     * @param id the unique identifier of the candidate
     * @return a {@link ResponseEntity} containing the {@link CandidateProfileDto}
     */
    @GetMapping("/candidates/{id}")
    public ResponseEntity<CandidateProfileDto> getCandidateProfile(@PathVariable Long id) {
        return ResponseEntity.ok(candidateService.getProfile(id));
    }

    /**
     * Updates the profile details for the currently authenticated employer.
     *
     * @param user    the authenticated employer user context
     * @param request the payload containing updated employer profile details
     * @return a {@link ResponseEntity} containing a success message map
     */
    @PutMapping("/profile")
    public ResponseEntity<Map<String, String>> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody UpdateEmployerProfileRequest request) {
        employerService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Employer profile updated successfully"));
    }

    /**
     * Deletes a job posting owned by the authenticated employer.
     *
     * @param id   the unique identifier of the job posting to delete
     * @param user the authenticated employer user context
     * @return a {@link ResponseEntity} containing a deletion confirmation message map
     */
    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Map<String, String>> deleteJob(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        jobService.deleteJob(id, user.getId());
        return ResponseEntity.ok(Map.of("message", "Job deleted successfully"));
    }
}
