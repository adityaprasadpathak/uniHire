package com.unihire.controllers;

import com.unihire.dto.ApplicationResponseDto;
import com.unihire.dto.ChangeApplicationStatusRequest;
import com.unihire.models.ApplicationStatus;
import com.unihire.models.User;
import com.unihire.services.ApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employer")
public class EmployerApplicationController {

    private final ApplicationService applicationService;

    public EmployerApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/jobs/{jobId}/applications")
    public ResponseEntity<List<ApplicationResponseDto>> getJobApplications(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(applicationService.getJobApplications(user.getId(), jobId));
    }

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
