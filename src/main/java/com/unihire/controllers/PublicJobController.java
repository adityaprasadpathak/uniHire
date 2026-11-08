package com.unihire.controllers;

import com.unihire.dto.JobResponseDto;
import com.unihire.services.job.PublicJobService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * REST controller for handling public job listing operations.
 * Provides endpoints to search published jobs, retrieve detailed job descriptions,
 * and record job view events for analytics tracking.
 */
@Tag(name = "Public Job Board", description = "Public job discovery, faceted search, and hybrid vector querying")
@RestController
@RequestMapping("/api/public/jobs")
public class PublicJobController {

    private final PublicJobService publicJobService;

    /**
     * Constructs a new {@code PublicJobController} with the specified {@link PublicJobService}.
     *
     * @param publicJobService the service handling public job business logic
     */
    public PublicJobController(PublicJobService publicJobService) {
        this.publicJobService = publicJobService;
    }

    /**
     * Searches for published job listings using optional search filters.
     * Supports filtering by keyword, location, and department along with pagination and sorting.
     *
     * @param keyword    optional keyword to search within job titles or descriptions
     * @param location   optional location filter for job listings
     * @param department optional department filter for job listings
     * @param pageable   pagination and sorting information (defaults to 20 items per page sorted by publishedAt)
     * @return a {@link ResponseEntity} containing a {@link Page} of {@link JobResponseDto} matching the criteria
     */
    @GetMapping
    public ResponseEntity<Page<JobResponseDto>> searchJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String department,
            @PageableDefault(size = 20, sort = "publishedAt") Pageable pageable) {
        
        return ResponseEntity.ok(publicJobService.searchJobs(keyword, location, department, pageable));
    }

    /**
     * Retrieves detailed information for a specific public job listing by its ID.
     *
     * @param id the unique identifier of the job listing
     * @return a {@link ResponseEntity} containing the details of the job as {@link JobResponseDto}
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobResponseDto> getJobDetails(@PathVariable Long id) {
        return ResponseEntity.ok(publicJobService.getJobDetails(id));
    }

    /**
     * Records a view event for a specific job listing.
     * Captures optional viewer user ID or anonymous session ID for analytics tracking.
     *
     * @param id        the unique identifier of the job listing being viewed
     * @param viewerId  optional user ID of the authenticated user viewing the job
     * @param sessionId optional session ID of the anonymous visitor viewing the job
     * @return a {@link ResponseEntity} with no content (HTTP 204 No Content)
     */
    @PostMapping("/{id}/view")
    public ResponseEntity<Void> recordJobView(
            @PathVariable Long id,
            @RequestParam(required = false) Long viewerId,
            @RequestParam(required = false) String sessionId) {
        publicJobService.recordJobView(id, viewerId, sessionId);
        return ResponseEntity.noContent().build();
    }
}
