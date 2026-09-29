package com.cdlms.sample;

import com.cdlms.lab.TubeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One physical tube or cup (Docs/Schema.md §3, ADR-024), numbered like {@code LAB-20260929-0007}. It
 * covers every test of one order that needs that tube. A rejected sample is never reused: the redraw
 * is a new sample pointing back at it, and the rejected one stays as a permanent record.
 */
@Entity
@Table(name = "samples")
public class Sample {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sample_code", nullable = false, unique = true, updatable = false)
    private String sampleCode;

    @Column(name = "lab_order_id", nullable = false, updatable = false)
    private UUID labOrderId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_tube_type", nullable = false, updatable = false)
    private TubeType requiredTubeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SampleStatus status = SampleStatus.ORDERED;

    @Enumerated(EnumType.STRING)
    @Column(name = "tube_type_used")
    private TubeType tubeTypeUsed;

    @Column(name = "tube_mismatch", nullable = false)
    private boolean tubeMismatch;

    @Column(name = "body_site")
    private String bodySite;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Column(name = "collected_by_user_id")
    private UUID collectedByUserId;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "received_by_user_id")
    private UUID receivedByUserId;

    @Column(name = "redraw_of_sample_id", updatable = false)
    private UUID redrawOfSampleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** The order lines this sample covers. */
    @ElementCollection
    @CollectionTable(name = "sample_items", joinColumns = @JoinColumn(name = "sample_id"))
    @Column(name = "lab_order_item_id", nullable = false)
    private Set<UUID> itemIds = new HashSet<>();

    protected Sample() {
    }

    public Sample(String sampleCode, UUID labOrderId, UUID patientId, TubeType requiredTubeType, UUID redrawOfSampleId) {
        this.sampleCode = sampleCode;
        this.labOrderId = labOrderId;
        this.patientId = patientId;
        this.requiredTubeType = requiredTubeType;
        this.redrawOfSampleId = redrawOfSampleId;
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

    // ---------------------------------------------------------------- transitions

    private void moveTo(SampleStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Sample " + sampleCode + " cannot move from " + status + " to " + target);
        }
        status = target;
    }

    /** Records the draw. {@code mismatch} is true when the tube used isn't the one the tests need. */
    public void collect(TubeType tubeUsed, boolean mismatch, String site, UUID byUserId, Instant at) {
        moveTo(SampleStatus.COLLECTED);
        tubeTypeUsed = tubeUsed;
        tubeMismatch = mismatch;
        bodySite = site;
        collectedByUserId = byUserId;
        collectedAt = at;
    }

    public void receive(UUID byUserId, Instant at) {
        moveTo(SampleStatus.RECEIVED_AT_LAB);
        receivedByUserId = byUserId;
        receivedAt = at;
    }

    /** Testing begins on an accepted sample (Phase 08). */
    public void startTesting() {
        moveTo(SampleStatus.IN_TESTING);
    }

    /** A result has been entered and now waits for the pathologist. */
    public void resultEntered() {
        moveTo(SampleStatus.RESULT_ENTERED);
    }

    /** The pathologist returned the result: the same sample goes back to testing. */
    public void returnToTesting() {
        moveTo(SampleStatus.IN_TESTING);
    }

    public void verified() {
        moveTo(SampleStatus.VERIFIED);
    }

    public void reportGenerated() {
        moveTo(SampleStatus.REPORT_GENERATED);
    }

    public void dispatched() {
        moveTo(SampleStatus.DISPATCHED);
    }

    public void reject() {
        moveTo(SampleStatus.REJECTED);
    }

    public void cancel() {
        moveTo(SampleStatus.CANCELLED);
    }

    public boolean isLive() {
        return status.isLive();
    }

    // ---------------------------------------------------------------- getters

    public UUID getId() {
        return id;
    }

    public String getSampleCode() {
        return sampleCode;
    }

    public UUID getLabOrderId() {
        return labOrderId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public TubeType getRequiredTubeType() {
        return requiredTubeType;
    }

    public SampleStatus getStatus() {
        return status;
    }

    public TubeType getTubeTypeUsed() {
        return tubeTypeUsed;
    }

    public boolean isTubeMismatch() {
        return tubeMismatch;
    }

    public String getBodySite() {
        return bodySite;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public UUID getCollectedByUserId() {
        return collectedByUserId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public UUID getReceivedByUserId() {
        return receivedByUserId;
    }

    public UUID getRedrawOfSampleId() {
        return redrawOfSampleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<UUID> getItemIds() {
        return itemIds;
    }
}
