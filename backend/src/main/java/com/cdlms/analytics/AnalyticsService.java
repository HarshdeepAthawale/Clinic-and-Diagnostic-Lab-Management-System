package com.cdlms.analytics;

import com.cdlms.analytics.AnalyticsDtos.Dashboard;
import com.cdlms.analytics.AnalyticsDtos.DayPoint;
import com.cdlms.analytics.AnalyticsDtos.Kpis;
import com.cdlms.analytics.AnalyticsDtos.StaffRow;
import com.cdlms.analytics.AnalyticsDtos.TatReport;
import com.cdlms.billing.BillingQueries;
import com.cdlms.common.ApiException;
import com.cdlms.inventory.InventoryService;
import com.cdlms.sample.SampleQueries;
import com.cdlms.sample.SampleQueries.UserRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The admin's insights (Docs/Design.md §5.7, ADR-026). Everything is computed from data the clinic already
 * records — payments, finished consultations, and the sample event log — so there is nothing to keep in sync.
 *
 * <p>A period is the last {@code days} clinic days including today. Charts get one point per day, with zeros
 * for quiet days (filled here, not in SQL). The previous period is the same length immediately before.
 */
@Service
public class AnalyticsService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 180;
    static final int TOP_TESTS = 8;

    private final AnalyticsQueries queries;
    private final BillingQueries billing;
    private final InventoryService inventory;
    private final SampleQueries users;
    private final ZoneId zone;

    public AnalyticsService(AnalyticsQueries queries, BillingQueries billing, InventoryService inventory,
                            SampleQueries users, @Value("${app.clinic.zone:Asia/Kolkata}") String zone) {
        this.queries = queries;
        this.billing = billing;
        this.inventory = inventory;
        this.users = users;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(Integer requestedDays) {
        Period period = period(requestedDays);
        Instant from = period.from();
        Instant to = period.to();
        Instant previousFrom = period.previousFrom();
        String zoneId = zone.getId();

        Map<LocalDate, Long> patients = queries.patientsPerDay(from, to, zoneId);
        Map<LocalDate, Long> registrations = queries.registrationsPerDay(from, to, zoneId);
        Map<LocalDate, Long> reports = queries.reportsPerDay(from, to, zoneId);
        Map<LocalDate, BigDecimal> revenue = queries.revenuePerDay(from, to, zoneId);

        List<DayPoint> series = new ArrayList<>();
        for (LocalDate day = period.firstDay(); !day.isAfter(period.lastDay()); day = day.plusDays(1)) {
            series.add(new DayPoint(day, patients.getOrDefault(day, 0L), registrations.getOrDefault(day, 0L),
                    revenue.getOrDefault(day, BigDecimal.ZERO), reports.getOrDefault(day, 0L)));
        }

        DayPoint today = series.getLast();
        Kpis kpis = new Kpis(today.patients(), today.revenue(), queries.samplesInProgress(), queries.medianTatMinutes(from, to),
                queries.patientsSeen(from, to), queries.patientsSeen(previousFrom, from),
                queries.revenue(from, to), queries.revenue(previousFrom, from),
                queries.reports(from, to), queries.reports(previousFrom, from), queries.medianTatMinutes(previousFrom, from),
                billing.outstandingAmount(), inventory.alerts().lowCount());

        return new Dashboard(period.days(), period.firstDay(), period.lastDay(), kpis, series,
                queries.topTests(from, to, TOP_TESTS), staff(from, to), queries.tat(from, to, null),
                queries.heatmap(from, to, zoneId, null));
    }

    /** Turnaround alone, optionally for one test: its stage breakdown and its daily median. */
    @Transactional(readOnly = true)
    public TatReport tat(UUID testId, Integer requestedDays) {
        Period period = period(requestedDays);
        var rows = queries.tat(period.from(), period.to(), testId);
        if (testId != null && rows.isEmpty()) {
            boolean exists = Boolean.TRUE.equals(queries.testExists(testId));
            if (!exists) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Test not found");
            }
        }
        return new TatReport(period.days(), period.firstDay(), period.lastDay(), rows,
                queries.heatmap(period.from(), period.to(), zone.getId(), testId));
    }

    /** Staff who did something in the period, busiest first; each row breaks the work down by measure. */
    private List<StaffRow> staff(Instant from, Instant to) {
        List<Object[]> actions = queries.staffActions(from, to);
        Map<UUID, Map<String, Long>> byUser = new LinkedHashMap<>();
        for (Object[] a : actions) {
            byUser.computeIfAbsent((UUID) a[0], k -> new LinkedHashMap<>()).merge((String) a[1], (Long) a[2], Long::sum);
        }
        Map<UUID, UserRef> who = users.users(byUser.keySet());
        return byUser.entrySet().stream()
                .filter(e -> who.containsKey(e.getKey()))
                .map(e -> new StaffRow(e.getKey(), who.get(e.getKey()).name(), who.get(e.getKey()).role(), e.getValue(),
                        e.getValue().values().stream().mapToLong(Long::longValue).sum()))
                .sorted(Comparator.comparingLong(StaffRow::total).reversed().thenComparing(StaffRow::name))
                .toList();
    }

    private Period period(Integer requested) {
        int days = requested == null ? DEFAULT_DAYS : requested;
        if (days < 1 || days > MAX_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose between 1 and " + MAX_DAYS + " days");
        }
        LocalDate last = LocalDate.now(zone);
        LocalDate first = last.minusDays(days - 1L);
        return new Period(days, first, last, first.atStartOfDay(zone).toInstant(), last.plusDays(1).atStartOfDay(zone).toInstant(),
                first.minusDays(days).atStartOfDay(zone).toInstant());
    }

    private record Period(int days, LocalDate firstDay, LocalDate lastDay, Instant from, Instant to, Instant previousFrom) {
    }
}
