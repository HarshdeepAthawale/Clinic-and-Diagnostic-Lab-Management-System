package com.cdlms.billing;

import com.cdlms.billing.BillingDtos.InvoiceView;
import com.cdlms.common.ClinicTime;
import com.cdlms.common.pdf.PdfRenderer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the printable invoice: letterhead, patient, lines, discount, totals, payments and a PAID stamp. */
@Component
public class InvoicePdf {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final PdfRenderer renderer;
    private final ClinicTime time;

    public InvoicePdf(PdfRenderer renderer, ClinicTime time) {
        this.renderer = renderer;
        this.time = time;
    }

    public byte[] render(InvoiceView invoice) {
        // Everything is formatted here so the template only prints strings.
        Map<String, Object> totals = new HashMap<>();
        totals.put("gross", rupees(invoice.gross()));
        totals.put("net", rupees(invoice.net()));
        if (invoice.discount() != null) {
            totals.put("discount", rupees(invoice.discount().amount()));
            totals.put("discountReason", invoice.discount().reason());
        }
        if (invoice.amountPaid().signum() > 0) {
            totals.put("paid", rupees(invoice.amountPaid()));
        }
        if (invoice.balance().signum() > 0) {
            totals.put("balance", rupees(invoice.balance()));
        }

        Map<String, Object> model = new HashMap<>();
        model.put("inv", invoice);
        model.put("t", totals);
        model.put("lines", invoice.lines().stream().filter(l -> l.voidedAt() == null)
                .map(l -> Map.of("description", l.description(), "amount", rupees(l.amount()))).toList());
        model.put("issuedOn", format(invoice.createdAt(), DATE));
        model.put("generatedOn", format(time.now(), DATE_TIME));
        model.put("paidOn", invoice.status() == Invoice.Status.PAID && invoice.paidAt() != null
                ? format(invoice.paidAt(), DATE) : null);
        model.put("hasPayments", !invoice.payments().isEmpty());
        model.put("payments", invoice.payments().stream().map(p -> Map.of(
                "on", format(p.receivedAt(), DATE_TIME),
                "method", switch (p.method()) {
                    case CASH -> "Cash";
                    case CARD -> "Card";
                    case UPI -> "UPI";
                },
                "reference", p.reference() == null ? "" : p.reference(),
                "amount", rupees(p.amount()))).toList());
        return renderer.render("invoice", model);
    }

    public String filename(InvoiceView invoice) {
        return invoice.invoiceCode() + ".pdf";
    }

    private String format(Instant instant, DateTimeFormatter formatter) {
        return instant.atZone(time.zone()).format(formatter);
    }

    /**
     * "Rs. 1,25,000.00" — Indian digit grouping. The standard PDF fonts have no rupee sign, so the
     * PDF spells it "Rs."; the web app shows ₹.
     */
    static String rupees(BigDecimal amount) {
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        String plain = value.abs().toPlainString();
        String whole = plain.substring(0, plain.indexOf('.'));
        String paise = plain.substring(plain.indexOf('.'));
        StringBuilder grouped = new StringBuilder();
        int n = whole.length();
        if (n <= 3) {
            grouped.append(whole);
        } else {
            String head = whole.substring(0, n - 3);
            List<String> pairs = new ArrayList<>();
            for (int i = head.length(); i > 0; i -= 2) {
                pairs.addFirst(head.substring(Math.max(0, i - 2), i));
            }
            grouped.append(String.join(",", pairs)).append(',').append(whole.substring(n - 3));
        }
        return (value.signum() < 0 ? "- " : "") + "Rs. " + grouped + paise;
    }
}
