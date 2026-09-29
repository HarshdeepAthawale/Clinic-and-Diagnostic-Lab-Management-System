package com.cdlms.result;

import com.cdlms.result.RangeCheck.Flag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The automatic reference-range check, without a database. */
class RangeCheckTest {

    private static BigDecimal n(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    /** Haemoglobin: normal 12.0–15.5, critical at 7.0 or 20.0. */
    private static Flag hb(String value) {
        return RangeCheck.flag(n(value), n("12.0"), n("15.5"), n("7.0"), n("20.0"));
    }

    @Test
    void valuesInsideTheNormalRangeAreNormalIncludingTheEdges() {
        assertThat(hb("13.2")).isEqualTo(Flag.NORMAL);
        assertThat(hb("12.0")).isEqualTo(Flag.NORMAL);
        assertThat(hb("15.5")).isEqualTo(Flag.NORMAL);
    }

    @Test
    void outsideTheRangeIsLowOrHigh() {
        assertThat(hb("11.9")).isEqualTo(Flag.LOW);
        assertThat(hb("15.6")).isEqualTo(Flag.HIGH);
    }

    @Test
    void criticalLimitsWinAndAreInclusive() {
        assertThat(hb("6.9")).isEqualTo(Flag.CRITICAL_LOW);
        assertThat(hb("7.0")).isEqualTo(Flag.CRITICAL_LOW);
        assertThat(hb("7.1")).isEqualTo(Flag.LOW);
        assertThat(hb("20.0")).isEqualTo(Flag.CRITICAL_HIGH);
        assertThat(hb("25")).isEqualTo(Flag.CRITICAL_HIGH);
    }

    @Test
    void missingLimitsAreNotChecked() {
        // An upper limit only (e.g. total cholesterol): nothing is ever LOW.
        assertThat(RangeCheck.flag(n("10"), null, n("200"), null, null)).isEqualTo(Flag.NORMAL);
        assertThat(RangeCheck.flag(n("250"), null, n("200"), null, null)).isEqualTo(Flag.HIGH);
        // A lower limit only (e.g. HDL).
        assertThat(RangeCheck.flag(n("500"), n("40"), null, null, null)).isEqualTo(Flag.NORMAL);
        assertThat(RangeCheck.flag(n("30"), n("40"), null, null, null)).isEqualTo(Flag.LOW);
        // No limits at all.
        assertThat(RangeCheck.flag(n("5"), null, null, null, null)).isEqualTo(Flag.NORMAL);
    }

    @Test
    void comparesNumbersNotScales() {
        assertThat(RangeCheck.flag(n("12"), n("12.000"), n("15.5"), null, null)).isEqualTo(Flag.NORMAL);
        assertThat(RangeCheck.flag(n("15.50"), n("12"), n("15.5"), null, null)).isEqualTo(Flag.NORMAL);
    }

    @Test
    void classifiesCriticalAndAbnormal() {
        assertThat(RangeCheck.isCritical(Flag.CRITICAL_HIGH)).isTrue();
        assertThat(RangeCheck.isCritical(Flag.HIGH)).isFalse();
        assertThat(RangeCheck.isAbnormal(Flag.LOW)).isTrue();
        assertThat(RangeCheck.isAbnormal(Flag.NORMAL)).isFalse();
        assertThat(RangeCheck.isAbnormal(null)).isFalse();
    }
}
