package com.unihire.controllers;

import com.unihire.dto.AdminCandidateDto;
import com.unihire.dto.AdminEmployerDto;
import com.unihire.dto.LockUserRequest;
import com.unihire.models.VerificationStatus;
import com.unihire.services.admin.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import java.util.Map;

/**
 * REST controller for administrator operations and system management.
 * <p>
 * Provides endpoints for managing employers, candidates, account lock statuses,
 * verification statuses, and document verifications. All endpoints under
 * {@code /api/admin} require administrator privileges.
 */
@Tag(name = "Administration", description = "Platform oversight, user lock toggling, and employer verification")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    /**
     * Constructs a new {@code AdminController} with the specified administrative service.
     *
     * @param adminService the service handling administrative logic and data operations
     */
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Retrieves a list of all employers registered in the system.
     *
     * @return a {@link ResponseEntity} containing a list of {@link AdminEmployerDto} objects representing all employers
     */
    @GetMapping("/employers")
    public ResponseEntity<List<AdminEmployerDto>> getAllEmployers() {
        return ResponseEntity.ok(adminService.getAllEmployers());
    }

    /**
     * Retrieves detailed information for a specific employer by ID.
     *
     * @param id the unique identifier of the employer to retrieve
     * @return a {@link ResponseEntity} containing the {@link AdminEmployerDto} for the specified employer
     */
    @GetMapping("/employers/{id}")
    public ResponseEntity<AdminEmployerDto> getEmployer(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getEmployer(id));
    }

    /**
     * Retrieves a list of all candidates registered in the system.
     *
     * @return a {@link ResponseEntity} containing a list of {@link AdminCandidateDto} objects representing all candidates
     */
    @GetMapping("/candidates")
    public ResponseEntity<List<AdminCandidateDto>> getAllCandidates() {
        return ResponseEntity.ok(adminService.getAllCandidates());
    }

    /**
     * Retrieves detailed information for a specific candidate by ID.
     *
     * @param id the unique identifier of the candidate to retrieve
     * @return a {@link ResponseEntity} containing the {@link AdminCandidateDto} for the specified candidate
     */
    @GetMapping("/candidates/{id}")
    public ResponseEntity<AdminCandidateDto> getCandidate(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getCandidate(id));
    }

    /**
     * Toggles or updates the account lock status of a user.
     *
     * @param id             the unique identifier of the user whose lock status is being updated
     * @param request        the request payload containing the locking parameters/reasons
     * @param authentication the authentication token of the administrator performing the action
     * @return a {@link ResponseEntity} containing a confirmation message map
     */
    @PutMapping("/users/{id}/lock")
    public ResponseEntity<?> toggleUserLock(
            @PathVariable Long id, 
            @RequestBody LockUserRequest request,
            Authentication authentication) {
        adminService.toggleUserLock(id, request, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "User lock status updated"));
    }

    /**
     * Updates the verification status of an employer profile.
     *
     * @param id   the unique identifier of the employer
     * @param body a map containing the target verification status string under the key {@code "status"}
     * @return a {@link ResponseEntity} containing a confirmation message map
     */
    @PutMapping("/employers/{id}/verify")
    public ResponseEntity<?> updateEmployerVerification(
            @PathVariable Long id, 
            @RequestBody Map<String, String> body) {
        VerificationStatus status = VerificationStatus.valueOf(body.get("status").toUpperCase());
        adminService.updateEmployerVerification(id, status);
        return ResponseEntity.ok(Map.of("message", "Employer verification updated"));
    }

    /**
     * Updates the verification status of a candidate profile.
     *
     * @param id   the unique identifier of the candidate
     * @param body a map containing the target verification status string under the key {@code "status"}
     * @return a {@link ResponseEntity} containing a confirmation message map
     */
    @PutMapping("/candidates/{id}/verify")
    public ResponseEntity<?> updateCandidateVerification(
            @PathVariable Long id, 
            @RequestBody Map<String, String> body) {
        VerificationStatus status = VerificationStatus.valueOf(body.get("status").toUpperCase());
        adminService.updateCandidateVerification(id, status);
        return ResponseEntity.ok(Map.of("message", "Candidate verification updated"));
    }

    /**
     * Updates the verification status of a user submitted document.
     *
     * @param id             the unique identifier of the document to verify
     * @param body           a map containing the target verification status string under the key {@code "status"}
     * @param authentication the authentication token of the administrator performing the verification
     * @return a {@link ResponseEntity} containing a confirmation message map
     */
    @PutMapping("/documents/{id}/verify")
    public ResponseEntity<?> verifyDocument(
            @PathVariable Long id, 
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        VerificationStatus status = VerificationStatus.valueOf(body.get("status").toUpperCase());
        adminService.verifyDocument(id, status, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Document verification updated"));
    }
}
