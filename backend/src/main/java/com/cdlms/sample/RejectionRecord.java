package com.cdlms.sample;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Why a sample was rejected, at which stage and by whom (Docs/Schema.md §3). Permanent — the
 * database rejects updates and deletes. Which reasons fit which stage is checked there too.
 */
@Entity
@Table(name = "rejection_records")
public class RejectionRecord {

    /** Where the sample was when it was rejected. */
    public enum Stage { RECEIVED_AT_LAB, IN_TESTING }

    public enum Reason {
        // At the receipt check
        HEMOLYZED, CLOTTED, INSUFFICIENT_VOLUME,
        // During testing
        SAMPLE_EXHAUSTED, SAMPLE_DEGRADED,
        // Either
        OTHER;

        public boolean allowedAt(Stage stage) {
            return allowed(stage).contains(this);
        }

        public static Set<Reason> allowed(Stage stage) {
            return stage == Stage.RECEIVED_AT_LAB
                    ? EnumSet.of(HEMOLYZED, CLOTTED, INSUFFICIENT_VOLUME, OTHER)
                    : EnumSet.of(SAMPLE_EXHAUSTED, SAMPLE_DEGRADED, OTHER);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sample_id", nullable = false, unique = true, updatable = false)
    private UUID sampleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejected_at_stage", nullable = false, updatable = false)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Reason reason;

    @Column(updatable = false)
    private String note;

    @Column(name = "flagged_by_user_id", nullable = false, updatable = false)
    private UUID flaggedByUserId;

    @Column(name = "flagged_at", nullable = false, updatable = false)
    private Instant flaggedAt;

    @Column(name = "front_desk_notified_at", updatable = false)
    private Instant frontDeskNotifiedAt;

    protected RejectionRecord() {
    }

    public RejectionRecord(UUID sampleId, Stage stage, Reason reason, String note, UUID flaggedByUserId, Instant at) {
        this.sampleId = sampleId;
        this.stage = stage;
        this.reason = reason;
        this.note = note;
        this.flaggedByUserId = flaggedByUserId;
        this.flaggedAt = at;
        // The front desk is notified in the same transaction the rejection is recorded.
        this.frontDeskNotifiedAt = at;
    }

    public UUID getSampleId() {
        return sampleId;
    }

    public Stage getStage() {
        return stage;
    }

    public Reason getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public UUID getFlaggedByUserId() {
        return flaggedByUserId;
    }

    public Instant getFlaggedAt() {
        return flaggedAt;
    }
}
