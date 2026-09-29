package com.cdlms.sample;

import com.cdlms.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Something a role needs to act on. A rejected sample tells the front desk to call the patient back;
 * they mark it handled once they have.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Type { SAMPLE_REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_role", nullable = false, updatable = false)
    private Role audienceRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false)
    private String message;

    @Column(name = "patient_id", updatable = false)
    private UUID patientId;

    @Column(name = "sample_id", updatable = false)
    private UUID sampleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "handled_by_user_id")
    private UUID handledByUserId;

    protected Notification() {
    }

    public Notification(Role audienceRole, Type type, String title, String message, UUID patientId, UUID sampleId) {
        this.audienceRole = audienceRole;
        this.type = type;
        this.title = title;
        this.message = message;
        this.patientId = patientId;
        this.sampleId = sampleId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void handle(UUID byUserId, Instant at) {
        handledAt = at;
        handledByUserId = byUserId;
    }

    public boolean isHandled() {
        return handledAt != null;
    }

    public UUID getId() {
        return id;
    }

    public Role getAudienceRole() {
        return audienceRole;
    }

    public Type getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getSampleId() {
        return sampleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
