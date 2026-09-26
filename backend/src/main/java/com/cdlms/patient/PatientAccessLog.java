package com.cdlms.patient;

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
 * One full-record read by a Doctor or Pathologist (Security.md §6). Append-only: the database
 * rejects updates and deletes, and this entity exposes no setters.
 */
@Entity
@Table(name = "patient_access_log")
public class PatientAccessLog {

    public enum Resource { EMR, CONSULTATIONS, PRESCRIPTION, LAB_REPORT, LAB_HISTORY }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Resource resource;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private Instant accessedAt;

    protected PatientAccessLog() {
    }

    public PatientAccessLog(UUID patientId, UUID userId, Resource resource, UUID resourceId) {
        this.patientId = patientId;
        this.userId = userId;
        this.resource = resource;
        this.resourceId = resourceId;
        this.accessedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Resource getResource() {
        return resource;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }
}
