package com.cdlms.lab;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests a doctor ordered for a patient (Docs/Schema.md §3), numbered like {@code LO-000123}. Created
 * from a consultation (at most one order per consultation — later tests are added to it) or directly
 * from the patient's record. Each test is a line with the price copied at order time.
 */
@Entity
@Table(name = "lab_orders")
public class LabOrder {

    public enum Status { ORDERED, CANCELLED }

    public enum Priority { ROUTINE, URGENT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_code", nullable = false, unique = true, updatable = false)
    private String orderCode;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "ordering_doctor_id", nullable = false, updatable = false)
    private UUID orderingDoctorId;

    @Column(name = "consultation_id", unique = true, updatable = false)
    private UUID consultationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.ROUTINE;

    @Column(name = "clinical_notes")
    private String clinicalNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ORDERED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @OneToMany(mappedBy = "labOrder", cascade = CascadeType.ALL)
    @OrderBy("createdAt")
    private List<LabOrderItem> items = new ArrayList<>();

    protected LabOrder() {
    }

    public LabOrder(String orderCode, UUID patientId, UUID orderingDoctorId, UUID consultationId) {
        this.orderCode = orderCode;
        this.patientId = patientId;
        this.orderingDoctorId = orderingDoctorId;
        this.consultationId = consultationId;
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

    /** Adds the test unless it is already live on this order; returns whether it was added. */
    public boolean addTest(LabTest test) {
        if (liveItems().stream().anyMatch(i -> i.getLabTestId().equals(test.getId()))) {
            return false;
        }
        items.add(new LabOrderItem(this, test));
        return true;
    }

    public void setDetails(Priority priority, String clinicalNotes) {
        this.priority = priority;
        this.clinicalNotes = clinicalNotes;
    }

    /** Cancels one line; when none are left the whole order is cancelled. */
    public void cancelItem(LabOrderItem item, UUID byUserId, Instant at) {
        item.cancel(at);
        if (liveItems().isEmpty()) {
            cancel(byUserId, at, "All tests removed");
        }
    }

    public void cancel(UUID byUserId, Instant at, String reason) {
        liveItems().forEach(i -> i.cancel(at));
        status = Status.CANCELLED;
        cancelledAt = at;
        cancelledByUserId = byUserId;
        cancellationReason = reason;
    }

    public List<LabOrderItem> liveItems() {
        return items.stream().filter(LabOrderItem::isLive).toList();
    }

    public boolean isOpen() {
        return status == Status.ORDERED;
    }

    public UUID getId() {
        return id;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getOrderingDoctorId() {
        return orderingDoctorId;
    }

    public UUID getConsultationId() {
        return consultationId;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getClinicalNotes() {
        return clinicalNotes;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public List<LabOrderItem> getItems() {
        return items;
    }
}
