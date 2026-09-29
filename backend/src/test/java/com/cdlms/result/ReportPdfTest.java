package com.cdlms.result;

import com.cdlms.lab.ValueType;
import com.cdlms.result.RangeCheck.Flag;
import com.cdlms.result.ResultDtos.ValueView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** How values, ranges and flags are worded on the report, without a database. */
class ReportPdfTest {

    private static BigDecimal n(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static ValueView numeric(String value, String low, String high, Flag flag) {
        return new ValueView(UUID.randomUUID(), UUID.randomUUID(), 1, "Haemoglobin", "g/dL", ValueType.NUMERIC, n(value), null,
                n(low), n(high), null, null, flag);
    }

    @Test
    void numbersDropTrailingZeros() {
        assertThat(ReportPdf.value(numeric("13.2000", "12", "15.5", Flag.NORMAL))).isEqualTo("13.2");
        assertThat(ReportPdf.value(numeric("140.0000", "135", "145", Flag.NORMAL))).isEqualTo("140");
        assertThat(ReportPdf.value(numeric("0.5000", null, null, null))).isEqualTo("0.5");
    }

    @Test
    void textIsShownAsEntered() {
        ValueView v = new ValueView(UUID.randomUUID(), UUID.randomUUID(), 1, "HBsAg", null, ValueType.TEXT, null, "Negative",
                null, null, null, null, null);
        assertThat(ReportPdf.value(v)).isEqualTo("Negative");
        assertThat(ReportPdf.range(v)).isEqualTo("—");
    }

    @Test
    void rangesReadNaturally() {
        assertThat(ReportPdf.range(numeric("1", "12.000", "15.500", null))).isEqualTo("12 – 15.5");
        assertThat(ReportPdf.range(numeric("1", null, "200", null))).isEqualTo("< 200");
        assertThat(ReportPdf.range(numeric("1", "40", null, null))).isEqualTo("> 40");
        assertThat(ReportPdf.range(numeric("1", null, null, null))).isEqualTo("—");
    }

    @Test
    void flagsAreWordedAndStyledByHowSerious() {
        assertThat(ReportPdf.flagLabel(Flag.NORMAL)).isEmpty();
        assertThat(ReportPdf.flagLabel(null)).isEmpty();
        assertThat(ReportPdf.flagLabel(Flag.LOW)).isEqualTo("LOW");
        assertThat(ReportPdf.flagLabel(Flag.CRITICAL_HIGH)).isEqualTo("CRITICAL HIGH");
        assertThat(ReportPdf.flagClass(Flag.HIGH)).isEqualTo("flag-high");
        assertThat(ReportPdf.flagClass(Flag.CRITICAL_LOW)).isEqualTo("flag-critical");
        assertThat(ReportPdf.flagClass(Flag.NORMAL)).isEmpty();
    }

    @Test
    void aRowReadsWithItsFlag() {
        assertThat(ReportPdf.shown(java.util.List.of(numeric("9.1", "12", "15.5", Flag.LOW))))
                .containsExactly("Haemoglobin 9.1 12 – 15.5 LOW");
    }
}
