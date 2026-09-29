package com.cdlms.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A consumable the lab runs on (Docs/Schema.md §5): tubes, cups, reagents, gloves. It is "low" when
 * its level drops below its threshold (a threshold of 0 means the item isn't watched). The level
 * itself is never set here — it changes only through {@link InventoryService#adjust}, which records
 * a movement each time.
 */
@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    public enum Category { TUBE, REAGENT, CONSUMABLE, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private String unit;

    @Column(name = "current_stock", nullable = false, insertable = true, updatable = false)
    private int currentStock;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItem() {
    }

    public InventoryItem(String name, Category category, String unit, int openingStock, int lowStockThreshold) {
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.currentStock = openingStock;
        this.lowStockThreshold = lowStockThreshold;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Renames, re-categorises, changes the unit or threshold, or retires/restores the item. */
    public void update(String name, Category category, String unit, int lowStockThreshold, boolean active) {
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.lowStockThreshold = lowStockThreshold;
        this.active = active;
    }

    public boolean isLow() {
        return active && currentStock < lowStockThreshold;
    }

    public boolean isOut() {
        return active && lowStockThreshold > 0 && currentStock == 0;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public String getUnit() {
        return unit;
    }

    public int getCurrentStock() {
        return currentStock;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
