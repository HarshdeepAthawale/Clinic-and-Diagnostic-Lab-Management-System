package com.cdlms.consultation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** A doctor's record of one visit (Docs/Schema.md §2). Editable by its doctor until completed, then locked. */
@Entity
@Table(name = "consultations")
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appointment_id", nullable = false, updatable = false, unique = true)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private UUID doctorId;

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

    private Short pulse;

    @Column(name = "temperature_c", precision = 4, scale = 1)
    private BigDecimal temperatureC;

    @Column(name = "weight_kg", precision = 5, scale = 1)
    private BigDecimal weightKg;

    private Short spo2;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Consultation() {
    }

    public Consultation(UUID appointmentId, UUID patientId, UUID doctorId, String chiefComplaint) {
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.chiefComplaint = chiefComplaint;
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

    public record Vitals(Short bpSystolic, Short bpDiastolic, Short pulse, BigDecimal temperatureC,
                         BigDecimal weightKg, Short spo2) {
    }

    void update(String chiefComplaint, String notes, String diagnosis, String advice, LocalDate followUpDate,
                Vitals vitals) {
        this.chiefComplaint = chiefComplaint;
        this.notes = notes;
        this.diagnosis = diagnosis;
        this.advice = advice;
        this.followUpDate = followUpDate;
        this.bpSystolic = vitals.bpSystolic();
        this.bpDiastolic = vitals.bpDiastolic();
        this.pulse = vitals.pulse();
        this.temperatureC = vitals.temperatureC();
        this.weightKg = vitals.weightKg();
        this.spo2 = vitals.spo2();
    }

    void complete(Instant at) {
        this.completedAt = at;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getDoctorId() {
        return doctorId;
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

    public Vitals getVitals() {
        return new Vitals(bpSystolic, bpDiastolic, pulse, temperatureC, weightKg, spo2);
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
