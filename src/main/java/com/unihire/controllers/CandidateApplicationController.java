package com.unihire.controllers;

import com.unihire.dto.ApplicationRequestDto;
import com.unihire.dto.ApplicationResponseDto;
import com.unihire.models.User;
import com.unihire.services.ApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/candidate")
public class CandidateApplicationController {

    private final ApplicationService applicationService;

    public CandidateApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

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

    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponseDto>> getMyApplications(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(applicationService.getCandidateApplications(user.getId()));
    }
}
