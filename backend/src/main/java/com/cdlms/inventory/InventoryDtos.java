package com.cdlms.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for inventory (Docs/API.md "Inventory"). */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    /** Why a level changed. {@code OPENING} is only used when an item is created with stock. */
    public enum Reason { OPENING, RESTOCK, USED, WASTAGE, CORRECTION }

    // ---------------------------------------------------------------- requests

    /** Admin: add an item. The opening stock, if any, is recorded as the first movement. */
    public record CreateItemRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull InventoryItem.Category category,
            @NotBlank @Size(max = 30) String unit,
            @Min(0) @Max(1_000_000) Integer openingStock,
            @NotNull @Min(0) @Max(1_000_000) Integer lowStockThreshold) {
    }

    /** Admin: change an item's details. The stock level is not editable here — use a movement. */
    public record UpdateItemRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull InventoryItem.Category category,
            @NotBlank @Size(max = 30) String unit,
            @NotNull @Min(0) @Max(1_000_000) Integer lowStockThreshold,
            Boolean active) {
    }

    /**
     * Lab technician or admin: change the level. {@code delta} is positive for a restock and negative for
     * use or wastage; a correction can go either way. The level can't drop below zero.
     */
    public record AdjustRequest(
            @NotNull Integer delta,
            @NotNull Reason reason,
            @Size(max = 300) String note) {
    }

    // ---------------------------------------------------------------- responses

    public record ItemView(UUID id, String name, InventoryItem.Category category, String unit, int currentStock,
                           int lowStockThreshold, boolean lowStock, boolean outOfStock, boolean active, Instant updatedAt) {
    }

    public record MovementView(UUID id, int delta, int stockAfter, Reason reason, String note, String actorName,
                               Instant createdAt) {
    }

    /** What needs restocking: counts and the worst offenders (emptiest relative to their threshold first). */
    public record Alerts(long lowCount, long outCount, List<ItemView> items) {
    }
}
