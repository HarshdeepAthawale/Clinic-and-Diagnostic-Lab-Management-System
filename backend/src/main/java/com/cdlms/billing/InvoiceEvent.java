package com.cdlms.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One step in an invoice's history — created, a test removed, a discount applied, a payment taken.
 * Append-only (V6 trigger), so every discount stays traceable to who applied it even if changed later.
 */
@Entity
@Table(name = "invoice_events")
public class InvoiceEvent {

    public enum Type { CREATED, LINE_VOIDED, DISCOUNT_APPLIED, PAYMENT_RECORDED, VOIDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Column(updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(updatable = false)
    private String note;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvoiceEvent() {
    }

    public InvoiceEvent(UUID invoiceId, Type type, BigDecimal amount, String note, UUID actorUserId) {
        this.invoiceId = invoiceId;
        this.type = type;
        this.amount = amount;
        this.note = note;
        this.actorUserId = actorUserId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
