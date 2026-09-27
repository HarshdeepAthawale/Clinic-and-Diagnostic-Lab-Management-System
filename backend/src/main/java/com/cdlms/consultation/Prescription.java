package com.cdlms.consultation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The medicines prescribed in one consultation. A draft while the consultation is open; issued with
 * a number like {@code RX-000123} when the consultation is completed, after which the database
 * rejects any change to it or its lines (V4 triggers).
 */
@Entity
@Table(name = "prescriptions")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "consultation_id", nullable = false, unique = true, updatable = false)
    private UUID consultationId;

    @Column(name = "prescription_code", unique = true)
    private String prescriptionCode;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<PrescriptionItem> items = new ArrayList<>();

    protected Prescription() {
    }

    public Prescription(UUID consultationId) {
        this.consultationId = consultationId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void addItem(PrescriptionItem.Line line) {
        items.add(new PrescriptionItem(this, items.size() + 1, line));
    }

    public void clearItems() {
        items.clear();
    }

    public void issue(String code, Instant at) {
        if (issuedAt != null) {
            throw new IllegalStateException("Prescription " + prescriptionCode + " is already issued");
        }
        prescriptionCode = code;
        issuedAt = at;
    }

    public boolean isIssued() {
        return issuedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConsultationId() {
        return consultationId;
    }

    public String getPrescriptionCode() {
        return prescriptionCode;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public List<PrescriptionItem> getItems() {
        return items;
    }
}
