package com.cdlms.inventory;

import com.cdlms.auth.AuthUser;
import com.cdlms.inventory.InventoryDtos.AdjustRequest;
import com.cdlms.inventory.InventoryDtos.Alerts;
import com.cdlms.inventory.InventoryDtos.CreateItemRequest;
import com.cdlms.inventory.InventoryDtos.ItemView;
import com.cdlms.inventory.InventoryDtos.MovementView;
import com.cdlms.inventory.InventoryDtos.UpdateItemRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The lab's consumables (Docs/API.md "Inventory"). */
@RestController
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/api/inventory")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'ADMIN')")
    public List<ItemView> list(@RequestParam(defaultValue = "") String q,
                               @RequestParam(required = false) InventoryItem.Category category,
                               @RequestParam(defaultValue = "false") boolean lowOnly,
                               @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(q, category, lowOnly, includeInactive);
    }

    /** What needs restocking right now: counts, and the emptiest items. */
    @GetMapping("/api/inventory/alerts")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'ADMIN')")
    public Alerts alerts() {
        return service.alerts();
    }

    @GetMapping("/api/inventory/{id}/movements")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'ADMIN')")
    public List<MovementView> movements(@PathVariable UUID id) {
        return service.movements(id);
    }

    @PostMapping("/api/inventory")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemView create(@AuthenticationPrincipal AuthUser admin, @Valid @RequestBody CreateItemRequest request) {
        return service.create(admin, request);
    }

    @PutMapping("/api/inventory/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ItemView update(@PathVariable UUID id, @Valid @RequestBody UpdateItemRequest request) {
        return service.update(id, request);
    }

    /** Restock, record use or wastage, or correct a count; recorded as a movement. */
    @PatchMapping("/api/inventory/{id}/stock")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'ADMIN')")
    public ItemView adjust(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                           @Valid @RequestBody AdjustRequest request) {
        return service.adjust(actor, id, request);
    }
}
