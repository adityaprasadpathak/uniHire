package com.unihire.controllers;

import com.unihire.dto.EmployerProfileDto;
import com.unihire.dto.UpdateEmployerProfileRequest;
import com.unihire.dto.ApplicationResponseDto;
import com.unihire.dto.CandidateSummaryDto;
import com.unihire.models.User;
import com.unihire.services.ApplicationService;
import com.unihire.services.CandidateService;
import com.unihire.services.EmployerService;
import com.unihire.services.JobService;
import com.unihire.services.SemanticSearchService;
import com.unihire.dto.CandidateProfileDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employer")
public class EmployerController {

    private final EmployerService employerService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final SemanticSearchService semanticSearchService;
    private final CandidateService candidateService;

    public EmployerController(EmployerService employerService, JobService jobService, ApplicationService applicationService, SemanticSearchService semanticSearchService, CandidateService candidateService) {
        this.employerService = employerService;
        this.jobService = jobService;
        this.applicationService = applicationService;
        this.semanticSearchService = semanticSearchService;
        this.candidateService = candidateService;
    }

    @GetMapping("/profile")
    public ResponseEntity<EmployerProfileDto> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(employerService.getProfile(user.getId()));
    }

    @GetMapping("/applications/{id}")
    public ResponseEntity<ApplicationResponseDto> getApplicationDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(applicationService.getApplicationDetails(user.getId(), id));
    }

    @GetMapping("/candidates/search")
    public ResponseEntity<List<CandidateSummaryDto>> searchCandidates(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK) {
        return ResponseEntity.ok(semanticSearchService.searchCandidates(query, topK));
    }

    @GetMapping("/candidates/{id}")
    public ResponseEntity<CandidateProfileDto> getCandidateProfile(@PathVariable Long id) {
        return ResponseEntity.ok(candidateService.getProfile(id));
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, String>> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody UpdateEmployerProfileRequest request) {
        employerService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Employer profile updated successfully"));
    }
}
