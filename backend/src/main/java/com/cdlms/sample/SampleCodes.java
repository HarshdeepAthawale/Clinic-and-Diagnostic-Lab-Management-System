package com.cdlms.sample;

import com.cdlms.common.ClinicTime;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Hands out sample codes like {@code LAB-20260929-0007}: the clinic's date plus a counter that
 * restarts each day. The counter row is bumped atomically, so two orders at the same moment can
 * never get the same number.
 */
@Component
public class SampleCodes {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final NamedParameterJdbcTemplate jdbc;
    private final ClinicTime time;

    public SampleCodes(NamedParameterJdbcTemplate jdbc, ClinicTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    public String next() {
        LocalDate day = time.today();
        Integer number = jdbc.queryForObject("""
                INSERT INTO sample_code_counters (day, last_number) VALUES (:day, 1)
                ON CONFLICT (day) DO UPDATE SET last_number = sample_code_counters.last_number + 1
                RETURNING last_number
                """, new MapSqlParameterSource("day", day), Integer.class);
        return format(day, number == null ? 1 : number);
    }

    static String format(LocalDate day, int number) {
        return "LAB-" + day.format(DAY) + "-" + String.format("%04d", number);
    }
}
