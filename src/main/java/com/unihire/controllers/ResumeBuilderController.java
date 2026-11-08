package com.unihire.controllers;

import com.unihire.dto.ResumeBuilderResponseDto;
import com.unihire.dto.ResumeParsingJobDto;
import com.unihire.dto.ResumeVersionDto;
import com.unihire.dto.TargetCustomizationRequest;
import com.unihire.models.User;
import com.unihire.services.resume.ResumeBuilderService;
import com.unihire.services.resume.ResumeExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST controller for managing candidate resume builder functionality.
 * Provides endpoints to retrieve resume builder profile data, upload and parse existing resumes,
 * generate targeted resume versions asynchronously, poll version status, delete versions,
 * and export generated resumes in formats such as PDF, DOCX, or JSON.
 */
@Tag(name = "Resume Builder", description = "AI resume generation, file uploading, background parsing, and template downloads")
@RestController
@RequestMapping("/api/resume-builder")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ResumeBuilderController {

    @Autowired
    private ResumeBuilderService resumeBuilderService;

    @Autowired
    private ResumeExportService resumeExportService;

    /**
     * Retrieves the overall resume builder profile data for the currently authenticated candidate.
     *
     * @param user the currently authenticated candidate user
     * @return a {@link ResponseEntity} containing {@link ResumeBuilderResponseDto} with candidate profile details
     */
    @GetMapping
    public ResponseEntity<ResumeBuilderResponseDto> getResumeBuilderData(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(resumeBuilderService.getCandidateResumeBuilderData(user.getId()));
    }

    /**
     * Uploads an existing resume file (e.g., PDF or DOCX) to initiate background parsing.
     *
     * @param user the currently authenticated candidate user
     * @param file the uploaded resume file
     * @return a {@link ResponseEntity} containing a {@link ResumeParsingJobDto} tracking the asynchronous parsing status
     */
    @PostMapping("/upload")
    public ResponseEntity<ResumeParsingJobDto> uploadExistingResume(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(resumeBuilderService.initiateResumeParsing(user.getId(), file));
    }

    /**
     * Initiates asynchronous generation of a targeted resume version based on customization criteria.
     *
     * @param user    the currently authenticated candidate user
     * @param request the target customization parameters and job details
     * @return a {@link ResponseEntity} with HTTP 202 (Accepted) status containing the newly created {@link ResumeVersionDto} in PROCESSING state
     */
    @PostMapping("/versions")
    public ResponseEntity<ResumeVersionDto> generateVersion(
            @AuthenticationPrincipal User user,
            @RequestBody TargetCustomizationRequest request) {
        ResumeVersionDto dto = resumeBuilderService.initiateVersionGeneration(user.getId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    /**
     * Retrieves the details and current processing status of a specific resume version by its ID.
     *
     * @param user the currently authenticated candidate user
     * @param id   the unique identifier of the resume version
     * @return a {@link ResponseEntity} containing the requested {@link ResumeVersionDto}
     */
    @GetMapping("/versions/{id}")
    public ResponseEntity<ResumeVersionDto> getVersion(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(resumeBuilderService.getVersion(id, user.getId()));
    }

    /**
     * Deletes a specific resume version belonging to the authenticated candidate.
     *
     * @param user the currently authenticated candidate user
     * @param id   the unique identifier of the resume version to delete
     * @return a {@link ResponseEntity} with status 204 (No Content)
     */
    @DeleteMapping("/versions/{id}")
    public ResponseEntity<Void> deleteVersion(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        resumeBuilderService.deleteVersion(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Exports and streams a specific resume version in the requested file format (PDF, DOCX, or JSON).
     *
     * @param user     the currently authenticated candidate user
     * @param id       the unique identifier of the resume version to download
     * @param format   the requested export format (defaults to "PDF", supports "DOCX" and "JSON")
     * @param response the {@link HttpServletResponse} object used to write the exported file content
     * @throws IOException if an I/O error occurs while streaming the file content to the response
     */
    @GetMapping("/versions/{id}/download")
    public void downloadVersion(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(defaultValue = "PDF") String format,
            HttpServletResponse response) throws IOException {
        resumeExportService.export(id, user.getId(), format, response);
    }
}
