package com.cdlms.lab;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.PageResponse;
import com.cdlms.lab.LabDtos.CancelRequest;
import com.cdlms.lab.LabDtos.LabOrderSummary;
import com.cdlms.lab.LabDtos.LabOrderView;
import com.cdlms.lab.LabDtos.LabTestRequest;
import com.cdlms.lab.LabDtos.LabTestSummary;
import com.cdlms.lab.LabDtos.LabTestView;
import com.cdlms.lab.LabDtos.OrderRequest;
import com.cdlms.user.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The lab test catalog and lab orders (Docs/API.md "Lab Tests & Orders"). */
@RestController
public class LabController {

    private final LabCatalogService catalog;
    private final LabOrderService orders;

    public LabController(LabCatalogService catalog, LabOrderService orders) {
        this.catalog = catalog;
        this.orders = orders;
    }

    // ---------------------------------------------------------------- catalog

    /** Active tests for everyone; admins can ask for retired ones too. */
    @GetMapping("/api/lab-tests")
    public List<LabTestSummary> tests(@AuthenticationPrincipal AuthUser caller,
                                      @RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "false") boolean includeInactive) {
        return catalog.list(q, includeInactive && caller.role() == Role.ADMIN);
    }

    @GetMapping("/api/lab-tests/{id}")
    public LabTestView test(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return catalog.get(id, caller.role() == Role.ADMIN);
    }

    @PostMapping("/api/lab-tests")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public LabTestView createTest(@Valid @RequestBody LabTestRequest request) {
        return catalog.create(request);
    }

    @PutMapping("/api/lab-tests/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public LabTestView updateTest(@PathVariable UUID id, @Valid @RequestBody LabTestRequest request) {
        return catalog.update(id, request);
    }

    // ---------------------------------------------------------------- orders

    /** Order tests from a consultation or directly; repeated orders in one consultation add to its open order. */
    @PostMapping("/api/lab-orders")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public LabOrderView order(@AuthenticationPrincipal AuthUser doctor, @Valid @RequestBody OrderRequest request) {
        return orders.order(doctor, request);
    }

    /** The lab's incoming queue. */
    @GetMapping("/api/lab-orders")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'PATHOLOGIST')")
    public PageResponse<LabOrderSummary> queue(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return orders.queue(Math.max(page, 0), Math.clamp(size, 1, 50));
    }

    @GetMapping("/api/lab-orders/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public List<LabOrderView> mine(@AuthenticationPrincipal AuthUser patient) {
        return orders.mine(patient);
    }

    @GetMapping("/api/lab-orders/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'LAB_TECHNICIAN', 'PATHOLOGIST')")
    public LabOrderView get(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return orders.get(caller, id);
    }

    @DeleteMapping("/api/lab-orders/{id}/items/{itemId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public LabOrderView removeItem(@AuthenticationPrincipal AuthUser doctor, @PathVariable UUID id,
                                   @PathVariable UUID itemId) {
        return orders.removeItem(doctor, id, itemId);
    }

    @PostMapping("/api/lab-orders/{id}/cancel")
    @PreAuthorize("hasRole('DOCTOR')")
    public LabOrderView cancel(@AuthenticationPrincipal AuthUser doctor, @PathVariable UUID id,
                               @Valid @RequestBody(required = false) CancelRequest request) {
        return orders.cancel(doctor, id, request == null ? null : request.reason());
    }

    /** The open order placed in a consultation; 204 when nothing has been ordered yet. */
    @GetMapping("/api/consultations/{consultationId}/lab-order")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<LabOrderView> forConsultation(@AuthenticationPrincipal AuthUser doctor,
                                                        @PathVariable UUID consultationId) {
        return orders.forConsultation(doctor, consultationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** A patient's orders. */
    @GetMapping("/api/patients/{patientId}/lab-orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public List<LabOrderSummary> forPatient(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID patientId) {
        return orders.forPatient(caller, patientId);
    }
}
