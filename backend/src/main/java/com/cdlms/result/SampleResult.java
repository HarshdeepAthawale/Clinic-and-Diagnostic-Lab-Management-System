package com.cdlms.result;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One attempt at the results of a sample (Docs/Schema.md §3, ADR-025): every parameter of every test
 * on the tube. It waits for the pathologist, who either verifies it or returns it for a retest — which
 * leaves it here as history and starts the next attempt. Once decided it never changes (V9 trigger).
 */
@Entity
@Table(name = "sample_results")
public class SampleResult {

    public enum Status { PENDING_VERIFICATION, VERIFIED, RETURNED_FOR_RETEST }

    public enum ReturnReason { IMPLAUSIBLE_VALUE, INCONSISTENT_WITH_HISTORY, CRITICAL_VALUE_CONFIRMATION, QC_CONCERN, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sample_id", nullable = false, updatable = false)
    private UUID sampleId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING_VERIFICATION;

    @Column(updatable = false)
    private String analyzer;

    @Column(name = "entered_by_user_id", nullable = false, updatable = false)
    private UUID enteredByUserId;

    @Column(name = "entered_at", nullable = false, updatable = false)
    private Instant enteredAt;

    @Column(name = "verified_by_pathologist_id")
    private UUID verifiedByPathologistId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "returned_by_pathologist_id")
    private UUID returnedByPathologistId;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_reason")
    private ReturnReason returnReason;

    @Column(name = "return_note")
    private String returnNote;

    @OneToMany(mappedBy = "sampleResult", cascade = CascadeType.ALL)
    @OrderBy("position")
    private List<ResultValue> values = new ArrayList<>();

    protected SampleResult() {
    }

    public SampleResult(UUID sampleId, int attemptNumber, String analyzer, UUID enteredByUserId, Instant enteredAt) {
        this.sampleId = sampleId;
        this.attemptNumber = attemptNumber;
        this.analyzer = analyzer;
        this.enteredByUserId = enteredByUserId;
        this.enteredAt = enteredAt;
    }

    public void addValue(ResultValue.Entry entry) {
        values.add(new ResultValue(this, entry));
    }

    public void verify(UUID pathologistId, Instant at) {
        requirePending();
        status = Status.VERIFIED;
        verifiedByPathologistId = pathologistId;
        verifiedAt = at;
    }

    public void returnForRetest(UUID pathologistId, ReturnReason reason, String note, Instant at) {
        requirePending();
        status = Status.RETURNED_FOR_RETEST;
        returnedByPathologistId = pathologistId;
        returnReason = reason;
        returnNote = note;
        returnedAt = at;
    }

    private void requirePending() {
        if (status != Status.PENDING_VERIFICATION) {
            throw new IllegalStateException("This result has already been " + status);
        }
    }

    public boolean isCritical() {
        return values.stream().anyMatch(v -> RangeCheck.isCritical(v.getFlag()));
    }

    public boolean isAbnormal() {
        return values.stream().anyMatch(v -> RangeCheck.isAbnormal(v.getFlag()));
    }

    public UUID getId() {
        return id;
    }

    public UUID getSampleId() {
        return sampleId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Status getStatus() {
        return status;
    }

    public String getAnalyzer() {
        return analyzer;
    }

    public UUID getEnteredByUserId() {
        return enteredByUserId;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }

    public UUID getVerifiedByPathologistId() {
        return verifiedByPathologistId;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public UUID getReturnedByPathologistId() {
        return returnedByPathologistId;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public ReturnReason getReturnReason() {
        return returnReason;
    }

    public String getReturnNote() {
        return returnNote;
    }

    public List<ResultValue> getValues() {
        return values;
    }
}
