package com.unihire.controllers;

import com.unihire.dto.JobResponseDto;
import com.unihire.dto.PublicEmployerDto;
import com.unihire.services.PublicEmployerService;
import com.unihire.services.PublicJobService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/employers")
public class PublicEmployerController {

    private final PublicEmployerService publicEmployerService;
    private final PublicJobService publicJobService;

    public PublicEmployerController(PublicEmployerService publicEmployerService, PublicJobService publicJobService) {
        this.publicEmployerService = publicEmployerService;
        this.publicJobService = publicJobService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicEmployerDto> getEmployerProfile(@PathVariable Long id) {
        return ResponseEntity.ok(publicEmployerService.getEmployerProfile(id));
    }

    @GetMapping("/{id}/jobs")
    public ResponseEntity<Page<JobResponseDto>> getEmployerJobs(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "publishedAt") Pageable pageable) {
        return ResponseEntity.ok(publicJobService.getEmployerJobs(id, pageable));
    }
}
