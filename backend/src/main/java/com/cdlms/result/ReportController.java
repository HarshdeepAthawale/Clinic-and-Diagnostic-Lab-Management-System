package com.cdlms.result;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.PageResponse;
import com.cdlms.result.ResultDtos.DispatchRequest;
import com.cdlms.result.ResultDtos.ReportRow;
import com.cdlms.result.ResultDtos.ReportView;
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

/** Lab reports: reading, PDF and dispatch (Docs/API.md "Reports"). Reports are keyed by their sample. */
@RestController
public class ReportController {

    private final ReportService service;
    private final ReportPdf pdf;

    public ReportController(ReportService service, ReportPdf pdf) {
        this.service = service;
        this.pdf = pdf;
    }

    /** The reports dispatched to the patient. */
    @GetMapping("/api/reports/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public List<ReportRow> mine(@AuthenticationPrincipal AuthUser patient) {
        return service.mine(patient);
    }

    /** Verified reports for orders the doctor placed. */
    @GetMapping("/api/reports/ordered-by-me")
    @PreAuthorize("hasRole('DOCTOR')")
    public PageResponse<ReportRow> orderedByMe(@AuthenticationPrincipal AuthUser doctor,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.orderedByMe(doctor, Math.max(page, 0), Math.clamp(size, 1, 50));
    }

    /** Verified reports that haven't been sent to the patient yet. */
    @GetMapping("/api/reports/to-dispatch")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public PageResponse<ReportRow> toDispatch(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.toDispatch(Math.max(page, 0), Math.clamp(size, 1, 50));
    }

    @GetMapping("/api/reports/{sampleId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'PATHOLOGIST', 'LAB_TECHNICIAN')")
    public ReportView get(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID sampleId) {
        return service.get(caller, sampleId);
    }

    /** Drawn from the verified values on request behind the same checks as the report — nothing is stored. */
    @GetMapping(value = "/api/reports/{sampleId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'PATHOLOGIST', 'LAB_TECHNICIAN')")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID sampleId,
                                      @RequestParam(defaultValue = "false") boolean download) {
        ReportView report = service.get(caller, sampleId);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(pdf.filename(report)).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(pdf.render(report));
    }

    @PostMapping("/api/reports/{sampleId}/dispatch")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public ReportView dispatch(@AuthenticationPrincipal AuthUser tech, @PathVariable UUID sampleId,
                               @Valid @RequestBody DispatchRequest request) {
        return service.dispatch(tech, sampleId, request.channel());
    }
}
