package com.cdlms.appointment;

import com.cdlms.appointment.AppointmentDtos.DaySlots;
import com.cdlms.appointment.AppointmentDtos.DoctorOption;
import com.cdlms.appointment.AppointmentDtos.WorkingBlock;
import com.cdlms.appointment.AppointmentDtos.WorkingHours;
import com.cdlms.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Doctors to book with, their free slots and their weekly working hours (Docs/API.md "Appointments"). */
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final AppointmentService service;

    public DoctorController(AppointmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    public List<DoctorOption> doctors() {
        return service.doctors();
    }

    @GetMapping("/{id}/slots")
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST')")
    public DaySlots slots(@PathVariable UUID id,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.slots(id, date);
    }

    @GetMapping("/{id}/working-hours")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    public List<WorkingBlock> workingHours(@PathVariable UUID id) {
        return service.workingHours(id);
    }

    /** The doctor themself, or an admin. */
    @PutMapping("/{id}/working-hours")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public List<WorkingBlock> replaceWorkingHours(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id,
                                                  @Valid @RequestBody WorkingHours request) {
        return service.replaceWorkingHours(caller, id, request);
    }
}
