package com.cdlms.result;

import com.cdlms.auth.AuthUser;
import com.cdlms.result.ResultDtos.PatientTrends;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Values across visits (Docs/API.md "Reports", ADR-029). */
@RestController
public class TrendController {

    private final TrendService service;

    public TrendController(TrendService service) {
        this.service = service;
    }

    @GetMapping("/api/patients/{patientId}/trends")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'PATHOLOGIST')")
    public PatientTrends trends(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID patientId) {
        return service.forPatient(caller, patientId);
    }
}
