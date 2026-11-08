package com.unihire.controllers;

import com.unihire.dto.JobResponseDto;
import com.unihire.dto.PublicEmployerDto;
import com.unihire.services.employer.PublicEmployerService;
import com.unihire.services.job.PublicJobService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller providing public REST API endpoints for viewing employer profiles and job listings.
 * <p>
 * These endpoints are publicly accessible without authentication.
 * </p>
 */
@RestController
@RequestMapping("/api/public/employers")
public class PublicEmployerController {

    private final PublicEmployerService publicEmployerService;
    private final PublicJobService publicJobService;

    /**
     * Constructs a new {@code PublicEmployerController} with required public services.
     *
     * @param publicEmployerService service for retrieving public employer profile information
     * @param publicJobService      service for retrieving publicly available job listings
     */
    public PublicEmployerController(PublicEmployerService publicEmployerService, PublicJobService publicJobService) {
        this.publicEmployerService = publicEmployerService;
        this.publicJobService = publicJobService;
    }

    /**
     * Retrieves the public profile details of an employer by their unique identifier.
     *
     * @param id the unique identifier of the employer
     * @return a {@link ResponseEntity} containing the {@link PublicEmployerDto}
     */
    @GetMapping("/{id}")
    public ResponseEntity<PublicEmployerDto> getEmployerProfile(@PathVariable Long id) {
        return ResponseEntity.ok(publicEmployerService.getEmployerProfile(id));
    }

    /**
     * Retrieves a paginated list of published job postings for a specific employer.
     *
     * @param id       the unique identifier of the employer
     * @param pageable pagination and sorting parameters (default page size 20, sorted by publishedAt)
     * @return a {@link ResponseEntity} containing a {@link Page} of {@link JobResponseDto} objects
     */
    @GetMapping("/{id}/jobs")
    public ResponseEntity<Page<JobResponseDto>> getEmployerJobs(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "publishedAt") Pageable pageable) {
        return ResponseEntity.ok(publicJobService.getEmployerJobs(id, pageable));
    }
}
