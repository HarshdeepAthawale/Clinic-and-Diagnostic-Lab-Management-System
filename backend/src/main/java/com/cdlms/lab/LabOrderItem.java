package com.cdlms.lab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One ordered test, with its name and price snapshotted so catalog edits never change the order. */
@Entity
@Table(name = "lab_order_items")
public class LabOrderItem {

    public enum Status { ORDERED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_order_id", nullable = false, updatable = false)
    private LabOrder labOrder;

    @Column(name = "lab_test_id", nullable = false, updatable = false)
    private UUID labTestId;

    @Column(name = "test_name", nullable = false, updatable = false)
    private String testName;

    @Column(name = "price_at_order", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal priceAtOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ORDERED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected LabOrderItem() {
    }

    LabOrderItem(LabOrder labOrder, LabTest test) {
        this.labOrder = labOrder;
        this.labTestId = test.getId();
        this.testName = test.getName();
        this.priceAtOrder = test.getPrice();
        // Set here too (not only on persist) so lines added in one request keep their order in memory.
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    void cancel(Instant at) {
        status = Status.CANCELLED;
        cancelledAt = at;
    }

    public boolean isLive() {
        return status != Status.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLabTestId() {
        return labTestId;
    }

    public String getTestName() {
        return testName;
    }

    public BigDecimal getPriceAtOrder() {
        return priceAtOrder;
    }

    public Status getStatus() {
        return status;
    }
}
