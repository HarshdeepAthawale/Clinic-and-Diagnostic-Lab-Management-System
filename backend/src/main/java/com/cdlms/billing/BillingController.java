package com.cdlms.billing;

import com.cdlms.auth.AuthUser;
import com.cdlms.billing.BillingDtos.DiscountRequest;
import com.cdlms.billing.BillingDtos.InvoiceSummary;
import com.cdlms.billing.BillingDtos.InvoiceView;
import com.cdlms.billing.BillingDtos.PaymentRequest;
import com.cdlms.billing.BillingQueries.Filter;
import com.cdlms.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Invoices, payments and discounts (Docs/API.md "Billing"). */
@RestController
public class BillingController {

    private final BillingService service;
    private final InvoicePdf pdf;

    public BillingController(BillingService service, InvoicePdf pdf) {
        this.service = service;
        this.pdf = pdf;
    }

    /** The billing counter: outstanding (oldest first), paid or all, searchable by patient or invoice number. */
    @GetMapping("/api/invoices")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public PageResponse<InvoiceSummary> list(@RequestParam(defaultValue = "OUTSTANDING") Filter status,
                                             @RequestParam(defaultValue = "") String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.list(status, q, Math.max(page, 0), Math.clamp(size, 1, 50));
    }

    @GetMapping("/api/invoices/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public List<InvoiceSummary> mine(@AuthenticationPrincipal AuthUser patient) {
        return service.mine(patient);
    }

    @GetMapping("/api/invoices/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST', 'ADMIN')")
    public InvoiceView get(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return service.get(caller, id);
    }

    /** Generated on request behind the same checks as the invoice — nothing is stored. */
    @GetMapping(value = "/api/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<byte[]> invoicePdf(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id,
                                             @RequestParam(defaultValue = "false") boolean download) {
        InvoiceView invoice = service.get(caller, id);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(pdf.filename(invoice)).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(pdf.render(invoice));
    }

    /** Record money received (part payments allowed, never more than the balance). */
    @PostMapping("/api/invoices/{id}/payments")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public InvoiceView pay(@AuthenticationPrincipal AuthUser receptionist, @PathVariable UUID id,
                           @Valid @RequestBody PaymentRequest request) {
        return service.pay(receptionist, id, request);
    }

    /** Set or remove the discount; who applied it is always recorded. */
    @PostMapping("/api/invoices/{id}/discount")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public InvoiceView discount(@AuthenticationPrincipal AuthUser staff, @PathVariable UUID id,
                                @Valid @RequestBody DiscountRequest request) {
        return service.discount(staff, id, request);
    }

    @GetMapping("/api/patients/{patientId}/invoices")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public List<InvoiceSummary> forPatient(@PathVariable UUID patientId) {
        return service.forPatient(patientId);
    }
}
