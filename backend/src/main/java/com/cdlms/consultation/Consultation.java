package com.cdlms.consultation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The doctor's record of one appointment (Docs/Schema.md §2). Editable while {@code DRAFT};
 * read-only once {@code COMPLETED} — the database rejects any later change (V4 trigger).
 */
@Entity
@Table(name = "consultations")
public class Consultation {

    public enum Status { DRAFT, COMPLETED }

    /** Everything the doctor writes; applied as a whole on each draft save. */
    public record Content(String chiefComplaint, String notes, String diagnosis, String advice, LocalDate followUpDate,
                          Short bpSystolic, Short bpDiastolic, Short pulseBpm, BigDecimal temperatureC,
                          Short spo2Percent, BigDecimal weightKg) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appointment_id", nullable = false, unique = true, updatable = false)
    private UUID appointmentId;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private UUID doctorId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "chief_complaint")
    private String chiefComplaint;

    @Column(columnDefinition = "text")
    private String notes;

    private String diagnosis;

    @Column(columnDefinition = "text")
    private String advice;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "bp_systolic")
    private Short bpSystolic;

    @Column(name = "bp_diastolic")
    private Short bpDiastolic;

    @Column(name = "pulse_bpm")
    private Short pulseBpm;

    @Column(name = "temperature_c", precision = 4, scale = 1)
    private BigDecimal temperatureC;

    @Column(name = "spo2_percent")
    private Short spo2Percent;

    @Column(name = "weight_kg", precision = 5, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Consultation() {
    }

    public Consultation(UUID appointmentId, UUID doctorId, UUID patientId) {
        this.appointmentId = appointmentId;
        this.doctorId = doctorId;
        this.patientId = patientId;
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

    public void apply(Content c) {
        requireDraft();
        chiefComplaint = c.chiefComplaint();
        notes = c.notes();
        diagnosis = c.diagnosis();
        advice = c.advice();
        followUpDate = c.followUpDate();
        bpSystolic = c.bpSystolic();
        bpDiastolic = c.bpDiastolic();
        pulseBpm = c.pulseBpm();
        temperatureC = c.temperatureC();
        spo2Percent = c.spo2Percent();
        weightKg = c.weightKg();
    }

    public void complete(Instant at) {
        requireDraft();
        status = Status.COMPLETED;
        completedAt = at;
    }

    private void requireDraft() {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Consultation " + id + " is completed");
        }
    }

    public boolean isDraft() {
        return status == Status.DRAFT;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public Status getStatus() {
        return status;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public String getNotes() {
        return notes;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getAdvice() {
        return advice;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public Short getBpSystolic() {
        return bpSystolic;
    }

    public Short getBpDiastolic() {
        return bpDiastolic;
    }

    public Short getPulseBpm() {
        return pulseBpm;
    }

    public BigDecimal getTemperatureC() {
        return temperatureC;
    }

    public Short getSpo2Percent() {
        return spo2Percent;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
