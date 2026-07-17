package com.unihire.controllers;

import com.unihire.dto.ApplicationResponseDto;
import com.unihire.models.User;
import com.unihire.services.AiFeatureService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AiFeatureController {

    private final AiFeatureService aiFeatureService;

    public AiFeatureController(AiFeatureService aiFeatureService) {
        this.aiFeatureService = aiFeatureService;
    }

    @GetMapping("/candidate/ai/cover-letter")
    public ResponseEntity<Map<String, String>> generateCoverLetter(
            @RequestParam Long jobId,
            @AuthenticationPrincipal User user) {
        String coverLetter = aiFeatureService.generateCoverLetter(user.getId(), jobId);
        return ResponseEntity.ok(Map.of("coverLetter", coverLetter));
    }

    @PostMapping("/employer/applications/{applicationId}/rescore")
    public ResponseEntity<ApplicationResponseDto> analyzeApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal User user) {
        ApplicationResponseDto response = aiFeatureService.analyzeApplication(user.getId(), applicationId);
        return ResponseEntity.ok(response);
    }
}
