package com.unihire.controllers;

import com.unihire.dto.ApplicationResponseDto;
import com.unihire.models.User;
import com.unihire.services.ai.AiFeatureService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import java.time.Duration;

/**
 * REST controller for AI-assisted features in the platform.
 * <p>
 * Provides endpoints for generating tailored AI cover letters for candidates
 * and performing AI-based candidate application scoring and analysis for employers.
 */
@RestController
@RequestMapping("/api")
public class AiFeatureController {

    private final AiFeatureService aiFeatureService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Constructs a new {@code AiFeatureController} with the specified AI feature service.
     *
     * @param aiFeatureService the service handling AI-powered cover letter generation and application analysis
     * @param redisTemplate the Redis template for tracking API rate limits
     */
    public AiFeatureController(AiFeatureService aiFeatureService, StringRedisTemplate redisTemplate) {
        this.aiFeatureService = aiFeatureService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generates a tailored AI cover letter for the authenticated candidate applied to a specific job.
     *
     * @param jobId the unique identifier of the job for which the cover letter is being generated
     * @param user  the currently authenticated user (candidate)
     * @return a {@link ResponseEntity} containing a map with the key {@code "coverLetter"} and the generated cover letter text
     */
    @GetMapping("/candidate/ai/cover-letter")
    public ResponseEntity<?> generateCoverLetter(
            @RequestParam Long jobId,
            @AuthenticationPrincipal User user) {
        String rateLimitKey = "rate_limit:ai:user:" + user.getId();
        Long currentRequests = redisTemplate.opsForValue().increment(rateLimitKey);
        if (currentRequests != null && currentRequests == 1) {
            redisTemplate.expire(rateLimitKey, Duration.ofDays(1));
        }
        if (currentRequests != null && currentRequests > 5) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", "Daily AI limit exceeded (5/day)"));
        }
        String coverLetter = aiFeatureService.generateCoverLetter(user.getId(), jobId);
        return ResponseEntity.ok(Map.of("coverLetter", coverLetter));
    }

    /**
     * Analyzes and rescores a job application using AI for an employer.
     *
     * @param applicationId the unique identifier of the job application to be analyzed
     * @param user          the currently authenticated user (employer)
     * @return a {@link ResponseEntity} containing the updated {@link ApplicationResponseDto} with AI analysis results
     */
    @PostMapping("/employer/applications/{applicationId}/rescore")
    public ResponseEntity<?> analyzeApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal User user) {
        String rateLimitKey = "rate_limit:ai:user:" + user.getId();
        Long currentRequests = redisTemplate.opsForValue().increment(rateLimitKey);
        if (currentRequests != null && currentRequests == 1) {
            redisTemplate.expire(rateLimitKey, Duration.ofDays(1));
        }
        if (currentRequests != null && currentRequests > 5) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", "Daily AI limit exceeded (5/day)"));
        }
        ApplicationResponseDto response = aiFeatureService.analyzeApplication(user.getId(), applicationId);
        return ResponseEntity.ok(response);
    }
}
