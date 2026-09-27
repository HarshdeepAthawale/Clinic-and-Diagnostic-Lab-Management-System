package com.cdlms.consultation;

import com.cdlms.common.ClinicTime;
import com.cdlms.consultation.ConsultationDtos.ItemView;
import com.cdlms.consultation.ConsultationDtos.PrescriptionView;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders a prescription as an A4 PDF from an XHTML template (OpenHTMLtoPDF on PDFBox, ADR-006).
 * Fonts are IBM Plex (SIL OFL, bundled under resources/fonts) so any Unicode name prints correctly.
 * Every value is HTML-escaped before it goes into the template.
 */
@Component
public class PrescriptionPdf {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final ClinicTime time;
    private final String clinicName;
    private final String clinicAddress;

    public PrescriptionPdf(ClinicTime time,
                           @Value("${app.clinic.name:CDLMS Clinic}") String clinicName,
                           @Value("${app.clinic.address:}") String clinicAddress) {
        this.time = time;
        this.clinicName = clinicName;
        this.clinicAddress = clinicAddress;
    }

    public byte[] render(PrescriptionView rx) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> font("IBMPlexSans-Regular.ttf"), "Plex", 400, FontStyle.NORMAL, true);
            builder.useFont(() -> font("IBMPlexSans-SemiBold.ttf"), "Plex", 600, FontStyle.NORMAL, true);
            builder.useFont(() -> font("IBMPlexMono-Medium.ttf"), "PlexMono", 500, FontStyle.NORMAL, true);
            builder.withHtmlContent(html(rx), null);
            builder.withProducer(clinicName);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render prescription " + rx.code(), e);
        }
    }

    private java.io.InputStream font(String file) {
        return PrescriptionPdf.class.getResourceAsStream("/fonts/" + file);
    }

    String html(PrescriptionView rx) {
        StringBuilder rows = new StringBuilder();
        for (ItemView item : rx.items()) {
            rows.append("<tr>")
                    .append("<td class=\"n\">").append(item.position()).append("</td>")
                    .append("<td><div class=\"med\">").append(e(item.medicine())).append("</div>")
                    .append(item.instructions() == null ? "" : "<div class=\"sub\">" + e(item.instructions()) + "</div>")
                    .append("</td>")
                    .append("<td>").append(e(orDash(item.dose()))).append("</td>")
                    .append("<td class=\"mono\">").append(e(item.frequency())).append("</td>")
                    .append("<td>").append(item.durationDays() == null ? "&#8212;" : item.durationDays() + " days").append("</td>")
                    .append("</tr>");
        }

        String doctorLine = join(rx.doctor().qualification(), rx.doctor().specialization());
        String registration = rx.doctor().registrationNumber() == null ? ""
                : "<div class=\"sub\">Reg. no. <span class=\"mono\">" + e(rx.doctor().registrationNumber()) + "</span></div>";
        String revision = rx.replaces() == null ? ""
                : "<div class=\"note\">Revised prescription. Replaces <span class=\"mono\">" + e(rx.replaces().code())
                + "</span> &#8212; " + e(rx.revisionReason()) + "</div>";
        String superseded = rx.supersededBy() == null ? ""
                : "<div class=\"void\">Replaced by <span class=\"mono\">" + e(rx.supersededBy().code())
                + "</span>. Do not dispense from this copy.</div>";
        String advice = rx.advice() == null ? ""
                : "<h2>Advice</h2><div class=\"advice\">" + e(rx.advice()).replace("\n", "<br/>") + "</div>";
        String diagnosis = rx.diagnosis() == null ? ""
                : "<div class=\"label\">Diagnosis</div><div class=\"value\">" + e(rx.diagnosis()) + "</div>";

        return """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"/><title>%s</title>
                <style>
                  @page { size: A4; margin: 18mm 16mm 20mm 16mm;
                          @bottom-left { content: "%s"; font-family: Plex; font-size: 8pt; color: #9a968f; }
                          @bottom-right { content: "Page " counter(page) " of " counter(pages); font-family: Plex; font-size: 8pt; color: #9a968f; } }
                  body { font-family: Plex; font-size: 10pt; color: #1c1b19; }
                  .mono { font-family: PlexMono; }
                  .head { width: 100%%; border-bottom: 2px solid #b42318; padding-bottom: 10px; }
                  .head td { vertical-align: top; }
                  .clinic { font-size: 16pt; font-weight: 600; letter-spacing: -0.3px; }
                  .sub { color: #6a6762; font-size: 8.5pt; margin-top: 2px; }
                  .code { text-align: right; }
                  .code .mono { font-size: 12pt; }
                  .rxmark { font-size: 22pt; font-weight: 600; color: #b42318; margin: 16px 0 4px; }
                  .grid { width: 100%%; margin-top: 14px; border-collapse: collapse; }
                  .grid td { vertical-align: top; width: 50%%; padding: 0 0 10px; }
                  .label { color: #6a6762; font-size: 8pt; text-transform: uppercase; letter-spacing: 0.6px; }
                  .value { font-weight: 600; margin-top: 2px; }
                  h2 { font-size: 10pt; font-weight: 600; margin: 18px 0 6px; }
                  table.meds { width: 100%%; border-collapse: collapse; }
                  table.meds th { text-align: left; font-size: 8pt; color: #6a6762; font-weight: 600; text-transform: uppercase;
                                  letter-spacing: 0.5px; border-bottom: 1px solid #d2d0cb; padding: 6px 6px; }
                  table.meds td { border-bottom: 1px solid #e4e2dd; padding: 8px 6px; vertical-align: top; }
                  table.meds tr { page-break-inside: avoid; }
                  .n { color: #9a968f; width: 18px; }
                  .med { font-weight: 600; }
                  .advice { line-height: 1.5; }
                  .note { margin-top: 10px; padding: 8px 10px; background: #faf9f7; border: 1px solid #e4e2dd; font-size: 9pt; }
                  .void { margin-top: 10px; padding: 8px 10px; background: #fcefed; border: 1px solid #b42318; color: #b42318; font-weight: 600; }
                  .sign { margin-top: 40px; width: 100%%; }
                  .sign td { vertical-align: bottom; }
                  .sigline { border-top: 1px solid #1c1b19; padding-top: 4px; width: 220px; }
                </style></head>
                <body>
                  <table class="head"><tr>
                    <td><div class="clinic">%s</div><div class="sub">%s</div></td>
                    <td class="code"><div class="label">Prescription</div><div class="mono">%s</div><div class="sub">%s</div></td>
                  </tr></table>
                  %s
                  <table class="grid"><tr>
                    <td><div class="label">Patient</div><div class="value">%s</div>
                        <div class="sub"><span class="mono">%s</span> &#183; %s years &#183; %s</div></td>
                    <td><div class="label">Doctor</div><div class="value">%s</div><div class="sub">%s</div>%s</td>
                  </tr><tr><td colspan="2">%s</td></tr></table>
                  <div class="rxmark">&#8478;</div>
                  <table class="meds">
                    <tr><th></th><th>Medicine</th><th>Dose</th><th>Frequency</th><th>Duration</th></tr>
                    %s
                  </table>
                  %s
                  %s
                  <table class="sign"><tr>
                    <td class="sub">Issued electronically on %s.<br/>Valid only with the prescription code above.</td>
                    <td style="text-align:right"><div class="sigline" style="margin-left:auto">%s</div></td>
                  </tr></table>
                </body></html>
                """.formatted(
                e(rx.code()), e(clinicName + " · " + rx.code()),
                e(clinicName), e(clinicAddress), e(rx.code()),
                e(DATE.format(rx.issuedAt().atZone(time.zone()))),
                superseded,
                e(rx.patient().fullName()), e(rx.patient().patientCode()), rx.patient().age(), e(gender(rx.patient().gender())),
                e(rx.doctor().fullName()), e(doctorLine), registration,
                diagnosis,
                rows,
                advice,
                revision,
                e(DATE_TIME.format(rx.issuedAt().atZone(time.zone()))), e(rx.doctor().fullName()));
    }

    private static String e(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private static String orDash(String value) {
        return value == null ? "—" : value;
    }

    private static String join(String a, String b) {
        if (a == null) {
            return b == null ? "" : b;
        }
        return b == null ? a : a + " · " + b;
    }

    private static String gender(String g) {
        return switch (g) {
            case "MALE" -> "Male";
            case "FEMALE" -> "Female";
            default -> "Other";
        };
    }
}
