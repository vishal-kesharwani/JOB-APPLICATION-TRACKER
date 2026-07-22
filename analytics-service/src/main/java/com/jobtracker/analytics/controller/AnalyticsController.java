package com.jobtracker.analytics.controller;

import com.jobtracker.analytics.dto.AnalyticsSummaryResponse;
import com.jobtracker.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Read-only reporting API. Analytics never writes application data — it only
 * serves aggregates it has derived from Kafka events (database-per-service).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary() {
        return AnalyticsSummaryResponse.of(
                analyticsService.totalApplications(),
                analyticsService.countsByStatus(),
                analyticsService.funnel());
    }

    @GetMapping("/by-status")
    public Map<String, Long> byStatus() {
        return analyticsService.countsByStatus();
    }

    @GetMapping("/by-company")
    public Map<String, Long> byCompany() {
        return analyticsService.countsByCompany();
    }

    @GetMapping("/by-resume")
    public Map<String, Long> byResumeVersion() {
        return analyticsService.countsByResumeVersion();
    }

    @GetMapping("/funnel")
    public Map<String, Long> funnel() {
        return analyticsService.funnel();
    }
}
