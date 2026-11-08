package com.unihire.controllers;

import com.unihire.dto.EmployerAnalyticsDto;
import com.unihire.models.User;
import com.unihire.services.admin.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for handling analytics endpoints for employers.
 * Provides APIs to retrieve metrics and insights regarding job postings, applications, and performance.
 */
@RestController
@RequestMapping("/api/employer/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Constructs a new {@code AnalyticsController} with the required {@link AnalyticsService}.
     *
     * @param analyticsService service handling employer analytics calculations and retrieval
     */
    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Retrieves aggregated analytics and performance metrics for the authenticated employer's dashboard.
     *
     * @param user the currently authenticated employer user extracted from security context
     * @return {@link ResponseEntity} containing the employer's dashboard metrics wrapped in {@link EmployerAnalyticsDto}
     */
    @GetMapping("/dashboard")
    public ResponseEntity<EmployerAnalyticsDto> getDashboardMetrics(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(analyticsService.getEmployerDashboardMetrics(user.getId()));
    }
}
