package com.cdlms.consultation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An issued e-prescription. Immutable: the database rejects updates and deletes, and this entity has
 * no setters. A correction is a new prescription whose {@code replacesId} points at the old one.
 */
@Entity
@Table(name = "prescriptions")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-readable code like {@code RX-000042}, assigned by the database. */
    @Generated
    @Column(insertable = false, updatable = false)
    private String code;

    @Column(name = "consultation_id", nullable = false, updatable = false)
    private UUID consultationId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private UUID doctorId;

    @Column(columnDefinition = "text", updatable = false)
    private String advice;

    @Column(name = "replaces_id", updatable = false)
    private UUID replacesId;

    @Column(name = "revision_reason", updatable = false)
    private String revisionReason;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "prescription_id", nullable = false, updatable = false)
    @OrderBy("position")
    private List<PrescriptionItem> items = new ArrayList<>();

    protected Prescription() {
    }

    public Prescription(Consultation consultation, String advice, List<PrescriptionItem> items, UUID replacesId,
                        String revisionReason, Instant issuedAt) {
        this.consultationId = consultation.getId();
        this.patientId = consultation.getPatientId();
        this.doctorId = consultation.getDoctorId();
        this.advice = advice;
        this.items = new ArrayList<>(items);
        this.replacesId = replacesId;
        this.revisionReason = revisionReason;
        this.issuedAt = issuedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public UUID getConsultationId() {
        return consultationId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public UUID getReplacesId() {
        return replacesId;
    }
}
