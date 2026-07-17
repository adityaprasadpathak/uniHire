package com.unihire.controllers;

import com.unihire.dto.JobResponseDto;
import com.unihire.services.PublicJobService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/jobs")
public class PublicJobController {

    private final PublicJobService publicJobService;

    public PublicJobController(PublicJobService publicJobService) {
        this.publicJobService = publicJobService;
    }

    @GetMapping
    public ResponseEntity<Page<JobResponseDto>> searchJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String department,
            @PageableDefault(size = 20, sort = "publishedAt") Pageable pageable) {
        
        return ResponseEntity.ok(publicJobService.searchJobs(keyword, location, department, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponseDto> getJobDetails(@PathVariable Long id) {
        return ResponseEntity.ok(publicJobService.getJobDetails(id));
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<Void> recordJobView(
            @PathVariable Long id,
            @RequestParam(required = false) Long viewerId,
            @RequestParam(required = false) String sessionId) {
        publicJobService.recordJobView(id, viewerId, sessionId);
        return ResponseEntity.noContent().build();
    }
}
