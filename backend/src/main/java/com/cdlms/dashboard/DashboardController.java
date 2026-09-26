package com.cdlms.dashboard;

import com.cdlms.auth.AuthUser;
import com.cdlms.dashboard.DashboardService.DashboardResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One dashboard endpoint per role, each gated to that role, all returning real data as widgets
 * (see {@link DashboardService}).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/patient")
    @PreAuthorize("hasRole('PATIENT')")
    public DashboardResponse patient(@AuthenticationPrincipal AuthUser user) {
        return service.forUser(user);
    }

    @GetMapping("/doctor")
    @PreAuthorize("hasRole('DOCTOR')")
    public DashboardResponse doctor(@AuthenticationPrincipal AuthUser user) {
        return service.forUser(user);
    }

    @GetMapping("/pathologist")
    @PreAuthorize("hasRole('PATHOLOGIST')")
    public DashboardResponse pathologist(@AuthenticationPrincipal AuthUser user) {
        return service.forUser(user);
    }

    @GetMapping("/receptionist")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public DashboardResponse receptionist(@AuthenticationPrincipal AuthUser user) {
        return service.forUser(user);
    }

    @GetMapping("/lab-technician")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public DashboardResponse labTechnician(@AuthenticationPrincipal AuthUser user) {
        return service.forUser(user);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public DashboardResponse admin(@AuthenticationPrincipal AuthUser user) {
        return service.forUser(user);
    }
}
