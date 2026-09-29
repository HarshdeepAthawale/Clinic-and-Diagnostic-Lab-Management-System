package com.cdlms.result;

import com.cdlms.common.ClinicTime;
import com.cdlms.common.pdf.PdfRenderer;
import com.cdlms.lab.ValueType;
import com.cdlms.result.RangeCheck.Flag;
import com.cdlms.result.ResultDtos.ReportView;
import com.cdlms.result.ResultDtos.ValueView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the printable lab report: letterhead, patient, each test's values with unit, reference range
 * and flag, and the pathologist's verification stamp (name, qualification, registration number).
 * Everything is formatted here so the template only prints strings.
 */
@Component
public class ReportPdf {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final PdfRenderer renderer;
    private final ClinicTime time;

    public ReportPdf(PdfRenderer renderer, ClinicTime time) {
        this.renderer = renderer;
        this.time = time;
    }

    public byte[] render(ReportView report) {
        Map<String, Object> model = new HashMap<>();
        model.put("r", report);
        model.put("gender", switch (report.patient().gender()) {
            case "MALE" -> "M";
            case "FEMALE" -> "F";
            default -> "Other";
        });
        model.put("collectedOn", report.collectedAt() == null ? "—" : format(report.collectedAt(), DATE));
        model.put("verifiedOn", format(report.verifiedAt(), DATE));
        model.put("verifiedAt", format(report.verifiedAt(), DATE_TIME));
        model.put("tests", report.tests().stream().map(t -> Map.of(
                "name", t.testName(),
                "code", t.testCode(),
                "rows", t.values().stream().map(ReportPdf::row).toList())).toList());
        return renderer.render("report", model);
    }

    public String filename(ReportView report) {
        return report.sampleCode() + ".pdf";
    }

    private String format(Instant instant, DateTimeFormatter formatter) {
        return instant.atZone(time.zone()).format(formatter);
    }

    private static Map<String, String> row(ValueView v) {
        return Map.of(
                "name", v.name(),
                "value", value(v),
                "unit", v.unit() == null ? "" : v.unit(),
                "range", range(v),
                "flag", flagLabel(v.flag()),
                "flagClass", flagClass(v.flag()));
    }

    /** A number without trailing zeros ("13.2", "140"), or the text as entered. */
    static String value(ValueView v) {
        if (v.valueType() == ValueType.NUMERIC && v.numericValue() != null) {
            return v.numericValue().stripTrailingZeros().toPlainString();
        }
        return v.textValue() == null ? "" : v.textValue();
    }

    /** "12 – 15.5", "< 200", "> 40", or a dash when there is no numeric range. */
    static String range(ValueView v) {
        if (v.refLow() != null && v.refHigh() != null) {
            return plain(v.refLow()) + " – " + plain(v.refHigh());
        }
        if (v.refHigh() != null) {
            return "< " + plain(v.refHigh());
        }
        if (v.refLow() != null) {
            return "> " + plain(v.refLow());
        }
        return "—";
    }

    static String flagLabel(Flag flag) {
        return flag == null || flag == Flag.NORMAL ? "" : switch (flag) {
            case LOW -> "LOW";
            case HIGH -> "HIGH";
            case CRITICAL_LOW -> "CRITICAL LOW";
            case CRITICAL_HIGH -> "CRITICAL HIGH";
            case NORMAL -> "";
        };
    }

    static String flagClass(Flag flag) {
        if (flag == null || flag == Flag.NORMAL) {
            return "";
        }
        return RangeCheck.isCritical(flag) ? "flag-critical" : flag == Flag.LOW ? "flag-low" : "flag-high";
    }

    private static String plain(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString();
    }

    /** Test helper: the text the report shows for each value, in order. */
    static List<String> shown(List<ValueView> values) {
        return values.stream().map(v -> v.name() + " " + value(v) + " " + range(v) + " " + flagLabel(v.flag())).toList();
    }
}
