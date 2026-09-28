package com.cdlms.billing;

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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One charge on an invoice: the consultation fee or one ordered test. The amount is copied when the
 * line is added. A test removed from the order voids its line instead of deleting it.
 */
@Entity
@Table(name = "invoice_items")
public class InvoiceItem {

    public enum Kind { CONSULTATION, LAB_TEST }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Kind kind;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "lab_order_item_id", unique = true, updatable = false)
    private UUID labOrderItemId;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvoiceItem() {
    }

    InvoiceItem(Invoice invoice, Kind kind, String description, BigDecimal amount, UUID labOrderItemId) {
        this.invoice = invoice;
        this.kind = kind;
        this.description = description;
        this.amount = amount;
        this.labOrderItemId = labOrderItemId;
        this.createdAt = Instant.now();
    }

    void voidAt(Instant at) {
        voidedAt = at;
    }

    public boolean isLive() {
        return voidedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getLabOrderItemId() {
        return labOrderItemId;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
