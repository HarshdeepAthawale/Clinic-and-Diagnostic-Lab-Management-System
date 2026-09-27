package com.cdlms.consultation;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.PageResponse;
import com.cdlms.consultation.ConsultationDtos.ConsultationRequest;
import com.cdlms.consultation.ConsultationDtos.ConsultationSummary;
import com.cdlms.consultation.ConsultationDtos.ConsultationView;
import com.cdlms.consultation.ConsultationDtos.FormularyItem;
import com.cdlms.consultation.ConsultationDtos.PrescriptionSummary;
import com.cdlms.consultation.ConsultationDtos.StartRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Consultations, prescriptions and the formulary (Docs/API.md "Consultations & Prescriptions"). */
@RestController
public class ConsultationController {

    private final ConsultationService service;
    private final PrescriptionPdf pdf;

    public ConsultationController(ConsultationService service, PrescriptionPdf pdf) {
        this.service = service;
        this.pdf = pdf;
    }

    // ---------------------------------------------------------------- consultations

    /** Start (or reopen) the consultation for one of the doctor's appointments. */
    @PostMapping("/api/consultations")
    @PreAuthorize("hasRole('DOCTOR')")
    public ConsultationView start(@AuthenticationPrincipal AuthUser doctor, @Valid @RequestBody StartRequest request) {
        return service.start(doctor, request.appointmentId());
    }

    @GetMapping("/api/consultations")
    @PreAuthorize("hasRole('DOCTOR')")
    public PageResponse<ConsultationSummary> mine(@AuthenticationPrincipal AuthUser doctor,
                                                  @RequestParam(defaultValue = "false") boolean today,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.mine(doctor, today, Math.max(page, 0), Math.clamp(size, 1, 50));
    }

    @GetMapping("/api/consultations/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public ConsultationView get(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return service.get(caller, id);
    }

    /** Save the draft (autosave). */
    @PutMapping("/api/consultations/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ConsultationView save(@AuthenticationPrincipal AuthUser doctor, @PathVariable UUID id,
                                 @Valid @RequestBody ConsultationRequest request) {
        return service.saveDraft(doctor, id, request);
    }

    /** Final save + finish: issues the prescription and completes the appointment. */
    @PostMapping("/api/consultations/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ConsultationView complete(@AuthenticationPrincipal AuthUser doctor, @PathVariable UUID id,
                                     @Valid @RequestBody ConsultationRequest request) {
        return service.complete(doctor, id, request);
    }

    /** A patient's completed visits. */
    @GetMapping("/api/patients/{patientId}/consultations")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public List<ConsultationSummary> history(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID patientId) {
        return service.historyForPatient(caller, patientId);
    }

    // ---------------------------------------------------------------- prescriptions

    @GetMapping("/api/prescriptions/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public List<PrescriptionSummary> myPrescriptions(@AuthenticationPrincipal AuthUser patient) {
        return service.myPrescriptions(patient);
    }

    /** The consultation behind an issued prescription (patients don't see the doctor's notes). */
    @GetMapping("/api/prescriptions/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public ConsultationView prescription(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return service.prescription(caller, id);
    }

    /** Generated on request behind the same checks as the record — there are no stored or guessable files. */
    @GetMapping(value = "/api/prescriptions/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public ResponseEntity<byte[]> prescriptionPdf(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id,
                                                  @RequestParam(defaultValue = "false") boolean download) {
        ConsultationView consultation = service.prescription(caller, id);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(pdf.filename(consultation)).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(pdf.render(consultation));
    }

    // ---------------------------------------------------------------- formulary

    @GetMapping("/api/formulary")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<FormularyItem> formulary(@RequestParam(defaultValue = "") String q) {
        return service.formulary(q);
    }
}
