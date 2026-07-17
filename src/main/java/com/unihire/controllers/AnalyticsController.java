package com.unihire.controllers;

import com.unihire.dto.EmployerAnalyticsDto;
import com.unihire.models.User;
import com.unihire.services.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employer/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<EmployerAnalyticsDto> getDashboardMetrics(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(analyticsService.getEmployerDashboardMetrics(user.getId()));
    }
}
