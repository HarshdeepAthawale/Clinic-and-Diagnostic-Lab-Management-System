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

/** Money received against an invoice at the counter. Append-only — the database rejects edits (V6). */
@Entity
@Table(name = "payments")
public class Payment {

    public enum Method { CASH, CARD, UPI }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Method method;

    @Column(updatable = false)
    private String reference;

    @Column(name = "received_by_user_id", nullable = false, updatable = false)
    private UUID receivedByUserId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected Payment() {
    }

    public Payment(UUID invoiceId, BigDecimal amount, Method method, String reference, UUID receivedByUserId, Instant at) {
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.method = method;
        this.reference = reference;
        this.receivedByUserId = receivedByUserId;
        this.receivedAt = at;
    }

    @PrePersist
    void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
