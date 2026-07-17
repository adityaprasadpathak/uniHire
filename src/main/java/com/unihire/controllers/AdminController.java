package com.unihire.controllers;

import com.unihire.dto.AdminCandidateDto;
import com.unihire.dto.AdminEmployerDto;
import com.unihire.dto.LockUserRequest;
import com.unihire.models.VerificationStatus;
import com.unihire.services.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/employers")
    public ResponseEntity<List<AdminEmployerDto>> getAllEmployers() {
        return ResponseEntity.ok(adminService.getAllEmployers());
    }

    @GetMapping("/employers/{id}")
    public ResponseEntity<AdminEmployerDto> getEmployer(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getEmployer(id));
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<AdminCandidateDto>> getAllCandidates() {
        return ResponseEntity.ok(adminService.getAllCandidates());
    }

    @GetMapping("/candidates/{id}")
    public ResponseEntity<AdminCandidateDto> getCandidate(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getCandidate(id));
    }

    @PutMapping("/users/{id}/lock")
    public ResponseEntity<?> toggleUserLock(
            @PathVariable Long id, 
            @RequestBody LockUserRequest request,
            Authentication authentication) {
        adminService.toggleUserLock(id, request, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "User lock status updated"));
    }

    @PutMapping("/employers/{id}/verify")
    public ResponseEntity<?> updateEmployerVerification(
            @PathVariable Long id, 
            @RequestBody Map<String, String> body) {
        VerificationStatus status = VerificationStatus.valueOf(body.get("status").toUpperCase());
        adminService.updateEmployerVerification(id, status);
        return ResponseEntity.ok(Map.of("message", "Employer verification updated"));
    }

    @PutMapping("/candidates/{id}/verify")
    public ResponseEntity<?> updateCandidateVerification(
            @PathVariable Long id, 
            @RequestBody Map<String, String> body) {
        VerificationStatus status = VerificationStatus.valueOf(body.get("status").toUpperCase());
        adminService.updateCandidateVerification(id, status);
        return ResponseEntity.ok(Map.of("message", "Candidate verification updated"));
    }

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
