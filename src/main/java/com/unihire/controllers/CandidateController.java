package com.unihire.controllers;

import com.unihire.dto.*;
import com.unihire.models.DocumentType;
import com.unihire.models.User;
import com.unihire.services.CandidateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/candidate")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping("/profile")
    public ResponseEntity<CandidateProfileDto> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(candidateService.getProfile(user.getId()));
    }

    @GetMapping("/profile/parsing-status")
    public ResponseEntity<Map<String, String>> getParsingStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(candidateService.getParsingStatus(user.getId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, String>> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody UpdateProfileRequest request) {
        candidateService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    @PostMapping("/profile/education")
    public ResponseEntity<Map<String, String>> addEducation(
            @AuthenticationPrincipal User user,
            @RequestBody EducationDto request) {
        candidateService.addEducation(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Education added successfully"));
    }

    @PostMapping("/profile/experience")
    public ResponseEntity<Map<String, String>> addExperience(
            @AuthenticationPrincipal User user,
            @RequestBody ExperienceDto request) {
        candidateService.addExperience(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Experience added successfully"));
    }

    @PostMapping("/profile/certification")
    public ResponseEntity<Map<String, String>> addCertification(
            @AuthenticationPrincipal User user,
            @RequestBody CertificationDto request) {
        candidateService.addCertification(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Certification added successfully"));
    }

    @PostMapping(value = "/profile/resume", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadResume(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {
        candidateService.uploadResume(user.getId(), file);
        return ResponseEntity.ok(Map.of("message", "Resume uploaded successfully. Parsing job enqueued."));
    }

    @PostMapping("/profile/resume/reparse")
    public ResponseEntity<Map<String, String>> reparseResume(@AuthenticationPrincipal User user) {
        candidateService.reparseResume(user.getId());
        return ResponseEntity.ok(Map.of("message", "Reparse job enqueued."));
    }

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
