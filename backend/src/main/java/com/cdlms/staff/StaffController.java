package com.cdlms.staff;

import com.cdlms.auth.AuthUser;
import com.cdlms.staff.StaffDtos.CreateStaffRequest;
import com.cdlms.staff.StaffDtos.CreatedStaff;
import com.cdlms.staff.StaffDtos.SetActiveRequest;
import com.cdlms.staff.StaffDtos.StaffView;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Staff accounts, admin only (Docs/API.md "Admin", ADR-032). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {

    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/staff")
    public List<StaffView> list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "true") boolean includeInactive) {
        return service.list(q, includeInactive);
    }

    /** The temporary password is in the response once; the response is never cached. */
    @PostMapping("/api/admin/staff")
    public ResponseEntity<CreatedStaff> create(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.create(request));
    }

    @PatchMapping("/api/admin/staff/{userId}/active")
    public StaffView setActive(@AuthenticationPrincipal AuthUser admin, @PathVariable UUID userId,
                               @Valid @RequestBody SetActiveRequest request) {
        return service.setActive(admin, userId, request.active());
    }
}
