package com.unihire.controllers;

import com.unihire.dto.ChangeJobStatusRequest;
import com.unihire.dto.JobRequestDto;
import com.unihire.dto.JobResponseDto;
import com.unihire.models.JobStatus;
import com.unihire.models.User;
import com.unihire.services.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employer/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponseDto> createJob(
            @AuthenticationPrincipal User user,
            @RequestBody JobRequestDto request) {
        return ResponseEntity.ok(jobService.createJob(user.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<JobResponseDto>> getJobs(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(jobService.getJobsByEmployer(user.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobResponseDto> updateJob(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @RequestBody JobRequestDto request) {
        return ResponseEntity.ok(jobService.updateJob(id, user.getId(), request));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<JobResponseDto> duplicateJob(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(jobService.duplicateJob(id, user.getId()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<JobResponseDto> changeStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @RequestBody ChangeJobStatusRequest request) {
        JobStatus newStatus = JobStatus.valueOf(request.getStatus().toUpperCase());
        return ResponseEntity.ok(jobService.changeJobStatus(id, user.getId(), newStatus));
    }

    @PostMapping("/resync-elasticsearch")
    public ResponseEntity<String> resyncElasticsearch() {
        jobService.resyncAllJobsToElasticsearch();
        return ResponseEntity.ok("Resync triggered successfully");
    }
}
