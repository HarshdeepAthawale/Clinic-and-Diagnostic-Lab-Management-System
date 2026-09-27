package com.cdlms.consultation;

import com.cdlms.common.ClinicTime;
import com.cdlms.common.pdf.PdfRenderer;
import com.cdlms.consultation.ConsultationDtos.ConsultationView;
import com.cdlms.consultation.ConsultationDtos.Vitals;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the printable prescription (letterhead, patient, diagnosis, medicines, signature block). */
@Component
public class PrescriptionPdf {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final PdfRenderer renderer;
    private final ClinicTime time;

    public PrescriptionPdf(PdfRenderer renderer, ClinicTime time) {
        this.renderer = renderer;
        this.time = time;
    }

    /** {@code consultation} must carry an issued prescription. */
    public byte[] render(ConsultationView consultation) {
        Map<String, Object> model = new HashMap<>();
        model.put("c", consultation);
        model.put("rx", consultation.prescription());
        model.put("gender", switch (consultation.patient().gender()) {
            case "MALE" -> "M";
            case "FEMALE" -> "F";
            default -> "Other";
        });
        model.put("visitOn", format(consultation.visitAt(), DATE));
        model.put("issuedOn", format(consultation.prescription().issuedAt(), DATE_TIME));
        model.put("followUpOn", consultation.followUpDate() == null ? null : consultation.followUpDate().format(DATE));
        model.put("vitals", vitalsLine(consultation.vitals()));
        return renderer.render("prescription", model);
    }

    public String filename(ConsultationView consultation) {
        return consultation.prescription().prescriptionCode() + ".pdf";
    }

    private String format(Instant instant, DateTimeFormatter formatter) {
        return instant.atZone(time.zone()).format(formatter);
    }

    /** "BP 124/82 mmHg · Pulse 78 bpm · …", or null when no vitals were recorded. */
    static String vitalsLine(Vitals v) {
        if (v == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (v.bpSystolic() != null && v.bpDiastolic() != null) {
            parts.add("BP " + v.bpSystolic() + "/" + v.bpDiastolic() + " mmHg");
        }
        if (v.pulseBpm() != null) {
            parts.add("Pulse " + v.pulseBpm() + " bpm");
        }
        if (v.temperatureC() != null) {
            parts.add("Temp " + v.temperatureC().stripTrailingZeros().toPlainString() + " °C");
        }
        if (v.spo2Percent() != null) {
            parts.add("SpO2 " + v.spo2Percent() + "%");
        }
        if (v.weightKg() != null) {
            parts.add("Weight " + v.weightKg().stripTrailingZeros().toPlainString() + " kg");
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }
}
