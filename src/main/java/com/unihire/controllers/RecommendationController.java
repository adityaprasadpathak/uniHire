package com.unihire.controllers;

import com.unihire.dto.RecommendedCandidateDto;
import com.unihire.dto.RecommendedJobDto;
import com.unihire.models.User;
import com.unihire.services.ai.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;

/**
 * REST controller for managing candidate and job recommendations.
 * Provides endpoints for employers to retrieve candidate recommendations for their job postings,
 * candidates to retrieve personalized job recommendations, and endpoints to manually trigger
 * recommendation re-generation.
 */
@Tag(name = "Recommendations", description = "Smart vector-based candidate and job matching recommendations")
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * Constructs a new {@code RecommendationController} with the specified {@link RecommendationService}.
     *
     * @param recommendationService the service managing candidate and job recommendation business logic
     */
    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * Retrieves recommended candidates for a specific job listing.
     * Accessible by employers looking to find top matching candidates for their job posting.
     *
     * @param jobId the unique identifier of the job listing
     * @param user  the currently authenticated employer user
     * @return a {@link ResponseEntity} containing a list of {@link RecommendedCandidateDto} matching the job
     */
    @GetMapping("/jobs/{jobId}/candidates")
    public ResponseEntity<List<RecommendedCandidateDto>> getRecommendedCandidates(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(recommendationService.getRecommendedCandidatesForJob(jobId));
    }

    /**
     * Retrieves recommended job listings tailored for the currently logged-in candidate.
     *
     * @param user the currently authenticated candidate user
     * @return a {@link ResponseEntity} containing a list of {@link RecommendedJobDto} tailored for the candidate
     */
    @GetMapping("/candidates/me/jobs")
    public ResponseEntity<List<RecommendedJobDto>> getRecommendedJobs(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(recommendationService.getRecommendedJobsForCandidate(user.getId()));
    }

    /**
     * Triggers manual re-generation of candidate recommendations for a specific job listing.
     * Allows employers to update recommendation matches on demand.
     *
     * @param jobId the unique identifier of the job listing
     * @param user  the currently authenticated employer user initiating the re-generation
     * @return a {@link ResponseEntity} containing a confirmation message
     */
    @PostMapping("/jobs/{jobId}/generate")
    public ResponseEntity<String> triggerJobRecommendations(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user) {
        recommendationService.generateRecommendationsForJob(jobId);
        return ResponseEntity.ok("Recommendation generation triggered for job " + jobId);
    }

    /**
     * Triggers manual re-generation of job recommendations for the currently logged-in candidate.
     * Allows candidates to refresh their job recommendations on demand.
     *
     * @param user the currently authenticated candidate user initiating the re-generation
     * @return a {@link ResponseEntity} containing a confirmation message
     */
    @PostMapping("/candidates/me/generate")
    public ResponseEntity<String> triggerCandidateRecommendations(
            @AuthenticationPrincipal User user) {
        recommendationService.generateRecommendationsForCandidate(user.getId());
        return ResponseEntity.ok("Recommendation generation triggered for candidate " + user.getId());
    }
}
