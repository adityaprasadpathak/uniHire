package com.unihire.controllers;

import com.unihire.dto.ApplicationResponseDto;
import com.unihire.dto.ChangeApplicationStatusRequest;
import com.unihire.models.ApplicationStatus;
import com.unihire.models.User;
import com.unihire.services.candidate.ApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import java.util.Map;

/**
 * REST controller for handling employer application management operations.
 * Provides endpoints for viewing candidate applications submitted for specific job postings
 * and updating the status of job applications.
 */
@Tag(name = "Employer Applications", description = "Applicant tracking system (ATS) candidate management and status updates")
@RestController
@RequestMapping("/api/employer")
public class EmployerApplicationController {

    private final ApplicationService applicationService;

    /**
     * Constructs a new {@code EmployerApplicationController} with the required service dependency.
     *
     * @param applicationService the service executing job application management logic
     */
    public EmployerApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * Retrieves all candidate applications submitted for a specific job posting.
     *
     * @param jobId the ID of the job posting
     * @param user the currently authenticated employer user
     * @return a {@link ResponseEntity} containing a list of {@link ApplicationResponseDto} objects
     */
    @GetMapping("/jobs/{jobId}/applications")
    public ResponseEntity<List<ApplicationResponseDto>> getJobApplications(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(applicationService.getJobApplications(user.getId(), jobId));
    }

    /**
     * Updates the status of a job application (e.g., IN_REVIEW, SHORTLISTED, ACCEPTED, REJECTED).
     *
     * @param applicationId the ID of the application being updated
     * @param user the currently authenticated employer user
     * @param request payload containing the new status string
     * @return a {@link ResponseEntity} containing the updated {@link ApplicationResponseDto} on success,
     *         a 400 Bad Request response if the status string is invalid,
     *         or a 403 Forbidden response if the operation is unauthorized
     */
    @PutMapping("/applications/{applicationId}/status")
    public ResponseEntity<?> changeApplicationStatus(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal User user,
            @RequestBody ChangeApplicationStatusRequest request) {
        try {
            ApplicationStatus newStatus = ApplicationStatus.valueOf(request.getStatus().toUpperCase());
            ApplicationResponseDto response = applicationService.changeApplicationStatus(user.getId(), applicationId, newStatus);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid application status"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }
}
