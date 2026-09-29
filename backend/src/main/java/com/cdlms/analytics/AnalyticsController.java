package com.cdlms.analytics;

import com.cdlms.analytics.AnalyticsDtos.Dashboard;
import com.cdlms.analytics.AnalyticsDtos.TatReport;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin insights (Docs/API.md "Admin"). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    /** KPIs, per-day series, most-ordered tests, staff activity and turnaround for the last {@code days} days. */
    @GetMapping("/api/admin/dashboard")
    public Dashboard dashboard(@RequestParam(required = false) Integer days) {
        return service.dashboard(days);
    }

    /** Turnaround by test type, or for one test with {@code testId}. */
    @GetMapping("/api/admin/analytics/tat")
    public TatReport tat(@RequestParam(required = false) UUID testId, @RequestParam(required = false) Integer days) {
        return service.tat(testId, days);
    }
}
