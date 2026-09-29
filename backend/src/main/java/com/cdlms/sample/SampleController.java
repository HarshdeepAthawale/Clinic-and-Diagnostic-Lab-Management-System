package com.cdlms.sample;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.common.PageResponse;
import com.cdlms.sample.SampleDtos.CollectRequest;
import com.cdlms.sample.SampleDtos.NotificationList;
import com.cdlms.sample.SampleDtos.ReceiveRequest;
import com.cdlms.sample.SampleDtos.SampleEvent;
import com.cdlms.sample.SampleDtos.SampleSummary;
import com.cdlms.sample.SampleDtos.SampleView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Samples and the front desk's notifications (Docs/API.md "Samples"). */
@RestController
public class SampleController {

    private final SampleService samples;
    private final NotificationService notifications;

    public SampleController(SampleService samples, NotificationService notifications) {
        this.samples = samples;
        this.notifications = notifications;
    }

    // ---------------------------------------------------------------- the lab's lists

    /** Samples waiting at one step: {@code status=ORDERED} (to collect) or {@code COLLECTED} (to receive). */
    @GetMapping("/api/samples")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'PATHOLOGIST')")
    public PageResponse<SampleSummary> waiting(@RequestParam(defaultValue = "ORDERED") SampleStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        if (status != SampleStatus.ORDERED && status != SampleStatus.COLLECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "status must be ORDERED or COLLECTED");
        }
        return samples.waiting(status, Math.max(page, 0), Math.clamp(size, 1, 50));
    }

    /** Looks a sample up by the code on its label — typed or read by a barcode scanner. */
    @GetMapping("/api/samples/by-code/{code}")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'PATHOLOGIST')")
    public SampleView byCode(@AuthenticationPrincipal AuthUser caller, @PathVariable String code) {
        return samples.byCode(caller, code);
    }

    @GetMapping("/api/samples/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public List<SampleView> mine(@AuthenticationPrincipal AuthUser patient) {
        return samples.mine(patient);
    }

    // ---------------------------------------------------------------- one sample

    @GetMapping("/api/samples/{id}/status")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'PATHOLOGIST', 'RECEPTIONIST', 'LAB_TECHNICIAN', 'ADMIN')")
    public SampleView status(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return samples.get(caller, id);
    }

    /** The full chain of custody: every step, who did it and when. */
    @GetMapping("/api/samples/{id}/events")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'PATHOLOGIST', 'ADMIN')")
    public List<SampleEvent> events(@PathVariable UUID id) {
        return samples.chainOfCustody(id);
    }

    @PostMapping("/api/samples/{id}/collect")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public SampleView collect(@AuthenticationPrincipal AuthUser tech, @PathVariable UUID id,
                              @Valid @RequestBody CollectRequest request) {
        return samples.collect(tech, id, request);
    }

    /** The receipt check: accept, or reject with a reason (which notifies the front desk and creates the redraw). */
    @PostMapping("/api/samples/{id}/receive")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public SampleView receive(@AuthenticationPrincipal AuthUser tech, @PathVariable UUID id,
                              @Valid @RequestBody ReceiveRequest request) {
        return samples.receive(tech, id, request);
    }

    /** The samples of one order, with their journeys. */
    @GetMapping("/api/lab-orders/{orderId}/samples")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'PATHOLOGIST', 'RECEPTIONIST', 'LAB_TECHNICIAN', 'ADMIN')")
    public List<SampleView> forOrder(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID orderId) {
        return samples.forOrder(caller, orderId);
    }

    // ---------------------------------------------------------------- front-desk notifications

    @GetMapping("/api/notifications")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public NotificationList notifications(@AuthenticationPrincipal AuthUser caller) {
        return notifications.open(caller);
    }

    @PostMapping("/api/notifications/{id}/handle")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public NotificationList handle(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return notifications.handle(caller, id);
    }
}
