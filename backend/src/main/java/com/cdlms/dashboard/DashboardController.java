package com.cdlms.dashboard;

import com.cdlms.auth.AuthUser;
import com.cdlms.user.Role;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One dashboard endpoint per role. Phase 01 only proves role gating end to end; later phases
 * fill these with real data (today's schedule, verification queue, revenue, ...).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    public record DashboardResponse(Role role, String title, String message) {
    }

    @GetMapping("/patient")
    @PreAuthorize("hasRole('PATIENT')")
    public DashboardResponse patient(@AuthenticationPrincipal AuthUser user) {
        return empty(user, "Patient dashboard");
    }

    @GetMapping("/doctor")
    @PreAuthorize("hasRole('DOCTOR')")
    public DashboardResponse doctor(@AuthenticationPrincipal AuthUser user) {
        return empty(user, "Doctor dashboard");
    }

    @GetMapping("/pathologist")
    @PreAuthorize("hasRole('PATHOLOGIST')")
    public DashboardResponse pathologist(@AuthenticationPrincipal AuthUser user) {
        return empty(user, "Pathologist dashboard");
    }

    @GetMapping("/receptionist")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public DashboardResponse receptionist(@AuthenticationPrincipal AuthUser user) {
        return empty(user, "Reception dashboard");
    }

    @GetMapping("/lab-technician")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public DashboardResponse labTechnician(@AuthenticationPrincipal AuthUser user) {
        return empty(user, "Lab dashboard");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public DashboardResponse admin(@AuthenticationPrincipal AuthUser user) {
        return empty(user, "Admin dashboard");
    }

    private DashboardResponse empty(AuthUser user, String title) {
        return new DashboardResponse(user.role(), title, "Nothing here yet — this area is built in later phases.");
    }
}
