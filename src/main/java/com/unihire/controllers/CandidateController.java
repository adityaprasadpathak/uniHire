package com.unihire.controllers;

import com.unihire.dto.*;
import com.unihire.models.DocumentType;
import com.unihire.models.User;
import com.unihire.services.candidate.CandidateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST controller for handling candidate-specific operations.
 * Provides API endpoints for candidate dashboard overview, profile retrieval and updates,
 * education/experience/certification management, resume uploading/reparsing, and document uploads.
 */
@Tag(name = "Candidate Profile", description = "Candidate profiles, experience, education, documents, and resume reparsing")
@RestController
@RequestMapping("/api/candidate")
public class CandidateController {

    private final CandidateService candidateService;

    /**
     * Constructs a new {@code CandidateController} with the specified service dependency.
     *
     * @param candidateService the service handling candidate-related business logic
     */
    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    /**
     * Retrieves the dashboard overview for the authenticated candidate.
     *
     * @param user the currently authenticated candidate user
     * @return a {@link ResponseEntity} containing {@link CandidateDashboardOverviewDto} with summary metrics
     */
    @GetMapping("/dashboard-overview")
    public ResponseEntity<CandidateDashboardOverviewDto> getDashboardOverview(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(candidateService.getDashboardOverview(user.getId()));
    }

    /**
     * Retrieves lightweight profile summary for dashboard header and metrics.
     * Prevents loading full documents, educations, and experiences on dashboard view.
     *
     * @param user the currently authenticated candidate user
     * @return a {@link ResponseEntity} containing {@link CandidateProfileSummaryDto}
     */
    @GetMapping("/profile/summary")
    public ResponseEntity<CandidateProfileSummaryDto> getProfileSummary(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(candidateService.getProfileSummary(user.getId()));
    }

    /**
     * Retrieves the candidate's profile details.
     *
     * @param user the currently authenticated candidate user
     * @return a {@link ResponseEntity} containing {@link CandidateProfileDto} with full candidate details
     */
    @GetMapping("/profile")
    public ResponseEntity<CandidateProfileDto> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(candidateService.getProfile(user.getId()));
    }

    /**
     * Retrieves the current resume parsing status for the authenticated candidate.
     *
     * @param user the currently authenticated candidate user
     * @return a {@link ResponseEntity} containing a map with the current parsing status
     */
    @GetMapping("/profile/parsing-status")
    public ResponseEntity<Map<String, String>> getParsingStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(candidateService.getParsingStatus(user.getId()));
    }

    /**
     * Updates the authenticated candidate's profile information.
     *
     * @param user the currently authenticated candidate user
     * @param request the request object containing updated candidate details
     * @return a {@link ResponseEntity} containing a message confirming profile update
     */
    @PutMapping("/profile")
    public ResponseEntity<Map<String, String>> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody UpdateProfileRequest request) {
        candidateService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    /**
     * Adds an education record to the candidate's profile.
     *
     * @param user the currently authenticated candidate user
     * @param request the payload containing education details
     * @return a {@link ResponseEntity} containing a message confirming addition of education details
     */
    @PostMapping("/profile/education")
    public ResponseEntity<Map<String, String>> addEducation(
            @AuthenticationPrincipal User user,
            @RequestBody EducationDto request) {
        candidateService.addEducation(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Education added successfully"));
    }

    /**
     * Adds a work experience record to the candidate's profile.
     *
     * @param user the currently authenticated candidate user
     * @param request the payload containing work experience details
     * @return a {@link ResponseEntity} containing a message confirming addition of work experience details
     */
    @PostMapping("/profile/experience")
    public ResponseEntity<Map<String, String>> addExperience(
            @AuthenticationPrincipal User user,
            @RequestBody ExperienceDto request) {
        candidateService.addExperience(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Experience added successfully"));
    }

    /**
     * Adds a certification record to the candidate's profile.
     *
     * @param user the currently authenticated candidate user
     * @param request the payload containing certification details
     * @return a {@link ResponseEntity} containing a message confirming addition of certification details
     */
    @PostMapping("/profile/certification")
    public ResponseEntity<Map<String, String>> addCertification(
            @AuthenticationPrincipal User user,
            @RequestBody CertificationDto request) {
        candidateService.addCertification(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Certification added successfully"));
    }

    /**
     * Uploads a candidate resume file and enqueues an asynchronous parsing job.
     *
     * @param user the currently authenticated candidate user
     * @param file the multipart file representing the candidate's resume
     * @return a {@link ResponseEntity} containing a message indicating successful upload and job enqueueing
     */
    @PostMapping(value = "/profile/resume", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadResume(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {
        candidateService.uploadResume(user.getId(), file);
        return ResponseEntity.ok(Map.of("message", "Resume uploaded successfully. Parsing job enqueued."));
    }

    /**
     * Triggers re-parsing of the authenticated candidate's previously uploaded resume.
     *
     * @param user the currently authenticated candidate user
     * @return a {@link ResponseEntity} containing a message indicating reparse job enqueueing
     */
    @PostMapping("/profile/resume/reparse")
    public ResponseEntity<Map<String, String>> reparseResume(@AuthenticationPrincipal User user) {
        candidateService.reparseResume(user.getId());
        return ResponseEntity.ok(Map.of("message", "Reparse job enqueued."));
    }

    /**
     * Uploads a document of a specified type for the authenticated candidate.
     *
     * @param user the currently authenticated candidate user
     * @param type the type of document being uploaded (e.g. RESUME, TRANSCRIPT, CERTIFICATE)
     * @param file the multipart file representing the document
     * @return a {@link ResponseEntity} containing a message confirming successful document upload
     */
    @PostMapping(value = "/documents/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadDocument(
            @AuthenticationPrincipal User user,
            @RequestParam("type") String type,
            @RequestParam("file") MultipartFile file) {
        
        DocumentType documentType = DocumentType.valueOf(type.toUpperCase());
        candidateService.uploadDocument(user.getId(), documentType, file);
        return ResponseEntity.ok(Map.of("message", "Document uploaded successfully"));
    }
}
