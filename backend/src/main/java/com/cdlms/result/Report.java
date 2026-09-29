package com.cdlms.result;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The report for a verified sample (Docs/Schema.md §3). It exists only once a pathologist has
 * verified the result — the database refuses to create it otherwise. The PDF is drawn from the verified
 * values when asked for; this row records that it was generated, when it was dispatched to the patient,
 * how, and when the patient first opened it.
 */
@Entity
@Table(name = "reports")
public class Report {

    public enum Channel { EMAIL, SMS, DOWNLOAD_LINK }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sample_id", nullable = false, unique = true, updatable = false)
    private UUID sampleId;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "generated_by_user_id", nullable = false, updatable = false)
    private UUID generatedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatched_channel")
    private Channel dispatchedChannel;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "dispatched_by_user_id")
    private UUID dispatchedByUserId;

    @Column(name = "receipt_confirmed_at")
    private Instant receiptConfirmedAt;

    /** Printed on the PDF as a QR code; lets anyone check the report is genuine (ADR-027). Fixed once issued. */
    @Column(name = "verification_code", nullable = false, updatable = false, length = 32)
    private String verificationCode;

    protected Report() {
    }

    public Report(UUID sampleId, UUID generatedByUserId, Instant generatedAt) {
        this.sampleId = sampleId;
        this.generatedByUserId = generatedByUserId;
        this.generatedAt = generatedAt;
        this.verificationCode = newVerificationCode();
    }

    /** 128 random bits as 32 hex characters: unguessable, and short enough for a QR code to stay easy to scan. */
    static String newVerificationCode() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    public void dispatch(Channel channel, UUID byUserId, Instant at) {
        if (dispatchedAt != null) {
            throw new IllegalStateException("This report has already been dispatched");
        }
        dispatchedChannel = channel;
        dispatchedByUserId = byUserId;
        dispatchedAt = at;
    }

    public boolean isDispatched() {
        return dispatchedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSampleId() {
        return sampleId;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Channel getDispatchedChannel() {
        return dispatchedChannel;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public UUID getDispatchedByUserId() {
        return dispatchedByUserId;
    }

    public Instant getReceiptConfirmedAt() {
        return receiptConfirmedAt;
    }

    public String getVerificationCode() {
        return verificationCode;
    }
}
