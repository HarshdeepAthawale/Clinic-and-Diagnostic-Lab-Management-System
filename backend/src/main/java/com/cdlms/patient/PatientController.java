package com.cdlms.patient;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.PageResponse;
import com.cdlms.patient.PatientDtos.PatientDetails;
import com.cdlms.patient.PatientDtos.PatientRecord;
import com.cdlms.patient.PatientDtos.PatientSummary;
import com.cdlms.patient.PatientDtos.RegisterPatientRequest;
import com.cdlms.patient.PatientDtos.RegisteredPatient;
import com.cdlms.patient.PatientDtos.UpdateClinicalRequest;
import com.cdlms.patient.PatientDtos.UpdateDemographicsRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Patient records (Docs/API.md "Patients"). Role gates here; record-level rules in {@link PatientService}. */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private static final int MAX_PAGE_SIZE = 50;

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST')")
    public PageResponse<PatientSummary> search(@AuthenticationPrincipal AuthUser caller,
                                               @RequestParam(defaultValue = "") String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.search(caller, q, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public RegisteredPatient register(@AuthenticationPrincipal AuthUser caller,
                                      @Valid @RequestBody RegisterPatientRequest request) {
        return service.register(caller, request);
    }

    /** The logged-in patient's own full record. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public PatientRecord me(@AuthenticationPrincipal AuthUser caller) {
        return service.myRecord(caller);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST', 'ADMIN')")
    public PatientDetails details(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return service.details(caller, id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public PatientDetails updateDemographics(@PathVariable UUID id, @Valid @RequestBody UpdateDemographicsRequest request) {
        return service.updateDemographics(id, request);
    }

    @PostMapping("/{id}/registration-code")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public RegisteredPatient reissueRegistrationCode(@PathVariable UUID id) {
        return service.reissueRegistrationCode(id);
    }

    /** Full record. Doctors need a care relationship; every doctor read is logged (ADR-015). */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public PatientRecord history(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return service.record(caller, id);
    }

    @PatchMapping("/{id}/clinical")
    @PreAuthorize("hasRole('DOCTOR')")
    public PatientRecord updateClinical(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id,
                                        @Valid @RequestBody UpdateClinicalRequest request) {
        return service.updateClinical(caller, id, request);
    }
}
