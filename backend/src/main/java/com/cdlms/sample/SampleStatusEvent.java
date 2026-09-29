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
import java.util.UUID;

/**
 * One step in a sample's chain of custody: what happened, who did it, when. Append-only — the
 * database rejects updates and deletes, so history can't be rewritten.
 */
@Entity
@Table(name = "sample_status_events")
public class SampleStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sample_id", nullable = false, updatable = false)
    private UUID sampleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SampleStatus status;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(updatable = false)
    private String detail;

    protected SampleStatusEvent() {
    }

    public SampleStatusEvent(UUID sampleId, SampleStatus status, UUID actorUserId, Instant occurredAt, String detail) {
        this.sampleId = sampleId;
        this.status = status;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
        this.detail = detail;
    }
}
