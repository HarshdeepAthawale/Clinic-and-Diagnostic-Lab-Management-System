package com.cdlms.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chart-ready payloads for the admin insights (Docs/API.md "Admin / Analytics"). All figures are real data from the log. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** One clinic day in the period. Days with nothing are included as zeros, so charts have no gaps. */
    public record DayPoint(LocalDate date, long patients, long registrations, BigDecimal revenue, long reports) {
    }

    /**
     * The headline numbers. "Previous" is the period of equal length just before, so the tiles can show
     * a change. The turnaround figures are medians (minutes, collection to report ready) and are null until
     * a report has been produced in that period.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Kpis(long patientsToday, BigDecimal revenueToday, long samplesInProgress, Long medianTatMinutes,
                       long patientsInPeriod, long patientsPrevious, BigDecimal revenueInPeriod, BigDecimal revenuePrevious,
                       long reportsInPeriod, long reportsPrevious, Long medianTatPreviousMinutes, BigDecimal outstanding,
                       long lowStock) {
    }

    public record TopTest(UUID testId, String code, String name, long orders, BigDecimal revenue) {
    }

    /** What one staff member did in the period, by measure (e.g. "Results verified": 14). */
    public record StaffRow(UUID userId, String name, String role, Map<String, Long> measures, long total) {
    }

    /**
     * Turnaround for one test type: minutes from collection to report ready, with the stages it is made of.
     * A sample with several tests counts towards each of them.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TatRow(UUID testId, String code, String name, long samples, long avgMinutes, long medianMinutes,
                         long p90Minutes, Long toLabMinutes, Long testingMinutes, Long verificationMinutes, long retested) {
    }

    /** Median turnaround for one test on one day (the heatmap cell). */
    public record HeatCell(UUID testId, LocalDate date, long medianMinutes, long samples) {
    }

    public record Dashboard(int days, LocalDate from, LocalDate to, Kpis kpis, List<DayPoint> series,
                            List<TopTest> topTests, List<StaffRow> staff, List<TatRow> tat, List<HeatCell> heatmap) {
    }

    /** Turnaround alone, optionally for a single test (with its daily median). */
    public record TatReport(int days, LocalDate from, LocalDate to, List<TatRow> tests, List<HeatCell> heatmap) {
    }
}
