package com.cdlms.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Now" and "today" as the clinic sees them. Days (queue numbering, today's schedule, working hours)
 * follow the clinic's time zone, not the server's.
 */
@Component
public class ClinicTime {

    private final ZoneId zone;
    private final Clock clock;

    @Autowired
    public ClinicTime(@Value("${app.clinic.zone:Asia/Kolkata}") String zone) {
        this(ZoneId.of(zone), Clock.systemUTC());
    }

    public ClinicTime(ZoneId zone, Clock clock) {
        this.zone = zone;
        this.clock = clock;
    }

    public ZoneId zone() {
        return zone;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public LocalDate dateOf(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    public Instant startOf(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }
}
