package com.cdlms.appointment;

import com.cdlms.appointment.AppointmentDtos.AppointmentDetail;
import com.cdlms.appointment.AppointmentDtos.AppointmentView;
import com.cdlms.appointment.AppointmentDtos.BookRequest;
import com.cdlms.appointment.AppointmentDtos.QueueBoard;
import com.cdlms.appointment.AppointmentDtos.StatusRequest;
import com.cdlms.appointment.AppointmentDtos.TokenRequest;
import com.cdlms.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Appointments and the live queue (Docs/API.md "Appointments"). Role gates here; rules in {@link AppointmentService}. */
@RestController
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @PostMapping("/api/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST')")
    public AppointmentView book(@AuthenticationPrincipal AuthUser caller, @Valid @RequestBody BookRequest request) {
        return service.book(caller, request);
    }

    /** {@code from}/{@code to} are clinic dates (inclusive); both default to today. */
    @GetMapping("/api/appointments")
    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    public List<AppointmentView> list(@AuthenticationPrincipal AuthUser caller,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) UUID doctorId) {
        return service.list(caller, from, to, doctorId);
    }

    @GetMapping("/api/appointments/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public Map<String, List<AppointmentView>> mine(@AuthenticationPrincipal AuthUser caller) {
        return service.mine(caller);
    }

    @GetMapping("/api/appointments/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    public AppointmentDetail detail(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return service.detail(caller, id);
    }

    @PatchMapping("/api/appointments/{id}/status")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'RECEPTIONIST')")
    public AppointmentView changeStatus(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id,
                                        @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(caller, id, request);
    }

    @PostMapping("/api/queue/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public AppointmentView issueToken(@AuthenticationPrincipal AuthUser caller, @Valid @RequestBody TokenRequest request) {
        return service.issueToken(caller, request);
    }

    @GetMapping("/api/queue")
    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    public QueueBoard queue(@AuthenticationPrincipal AuthUser caller) {
        return service.queue(caller);
    }
}
