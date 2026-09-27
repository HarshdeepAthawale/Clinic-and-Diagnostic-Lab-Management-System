package com.cdlms.consultation;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.PageResponse;
import com.cdlms.consultation.ConsultationDtos.ConsultationSummary;
import com.cdlms.consultation.ConsultationDtos.ConsultationView;
import com.cdlms.consultation.ConsultationDtos.MedicineSuggestion;
import com.cdlms.consultation.ConsultationDtos.PrescriptionRequest;
import com.cdlms.consultation.ConsultationDtos.PrescriptionView;
import com.cdlms.consultation.ConsultationDtos.ReviseRequest;
import com.cdlms.consultation.ConsultationDtos.UpdateConsultationRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Consultations and prescriptions (Docs/API.md "Consultations & Prescriptions"). Role gates here;
 * record-level rules (own visit, care relationship, access log) in {@link ConsultationService}.
 */
@RestController
public class ConsultationController {

    private final ConsultationService service;
    private final PrescriptionPdf pdf;

    public ConsultationController(ConsultationService service, PrescriptionPdf pdf) {
        this.service = service;
        this.pdf = pdf;
    }

    @PostMapping("/api/appointments/{appointmentId}/consultation")
    @PreAuthorize("hasRole('DOCTOR')")
    public ConsultationView open(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID appointmentId) {
        return service.open(caller, appointmentId);
    }

    @GetMapping("/api/consultations/mine")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public PageResponse<ConsultationSummary> mine(@AuthenticationPrincipal AuthUser caller,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.mine(caller, page, size);
    }

    @GetMapping("/api/consultations/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public ConsultationView get(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return service.get(caller, id);
    }

    @PatchMapping("/api/consultations/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ConsultationView update(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id,
                                   @Valid @RequestBody UpdateConsultationRequest request) {
        return service.update(caller, id, request);
    }

    @PostMapping("/api/consultations/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ConsultationView complete(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return service.complete(caller, id);
    }

    @PostMapping("/api/consultations/{id}/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionView prescribe(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id,
                                      @Valid @RequestBody PrescriptionRequest request) {
        return service.prescribe(caller, id, request);
    }

    @GetMapping("/api/patients/{patientId}/consultations")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public List<ConsultationSummary> forPatient(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID patientId) {
        return service.forPatient(caller, patientId);
    }

    @GetMapping("/api/prescriptions/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public List<PrescriptionView> myPrescriptions(@AuthenticationPrincipal AuthUser caller) {
        return service.myPrescriptions(caller);
    }

    @GetMapping("/api/prescriptions/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public PrescriptionView prescription(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return service.prescription(caller, id);
    }

    /** Same access check (and access-log entry) as viewing the prescription. */
    @GetMapping("/api/prescriptions/{id}/pdf")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public ResponseEntity<byte[]> prescriptionPdf(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        PrescriptionView rx = service.prescription(caller, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(rx.code() + ".pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(pdf.render(rx));
    }

    @PostMapping("/api/prescriptions/{id}/revise")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionView revise(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id,
                                   @Valid @RequestBody ReviseRequest request) {
        return service.revise(caller, id, request);
    }

    @GetMapping("/api/medicines")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<MedicineSuggestion> medicines(@RequestParam(defaultValue = "") String q) {
        return service.medicines(q);
    }
}
