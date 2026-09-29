package com.cdlms.result;

import com.cdlms.auth.AuthUser;
import com.cdlms.result.ResultDtos.AcknowledgeRequest;
import com.cdlms.result.ResultDtos.Acknowledgement;
import com.cdlms.result.ResultDtos.CriticalAlerts;
import com.cdlms.result.ResultDtos.CriticalSummary;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Critical value alerts (Docs/API.md "Reports", ADR-028). */
@RestController
public class CriticalAlertController {

    private final CriticalAlertService service;

    public CriticalAlertController(CriticalAlertService service) {
        this.service = service;
    }

    /** The doctor's own critical results still waiting to be acknowledged. */
    @GetMapping("/api/reports/critical")
    @PreAuthorize("hasRole('DOCTOR')")
    public CriticalAlerts mine(@AuthenticationPrincipal AuthUser doctor) {
        return service.forDoctor(doctor);
    }

    /** Every critical result waiting for its doctor: the lab may need to phone them. */
    @GetMapping("/api/reports/critical/all")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public CriticalAlerts all() {
        return service.forLab();
    }

    /** The admin's view: how many are waiting and since when. */
    @GetMapping("/api/reports/critical/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public CriticalSummary summary() {
        return service.summary();
    }

    @PostMapping("/api/reports/{sampleId}/acknowledge-critical")
    @PreAuthorize("hasRole('DOCTOR')")
    public Acknowledgement acknowledge(@AuthenticationPrincipal AuthUser doctor, @PathVariable UUID sampleId,
                                       @Valid @RequestBody(required = false) AcknowledgeRequest request) {
        return service.acknowledge(doctor, sampleId, request == null ? null : request.note());
    }
}
