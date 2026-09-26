package com.cdlms.patient;

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
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/** The single shared patient record used by both the clinic and the lab (Docs/PRD.md §1). */
@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-readable ID like {@code PID-000184}, assigned by the database on insert. */
    @Generated
    @Column(name = "patient_code", insertable = false, updatable = false)
    private String patientCode;

    /** Null when the front desk registers a walk-in who has no login yet. */
    @Column(name = "user_id", unique = true)
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private LocalDate dob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private String phone;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "known_allergies", columnDefinition = "text")
    private String knownAllergies;

    @Column(name = "blood_group")
    private String bloodGroup;

    @Column(name = "medical_history", columnDefinition = "text")
    private String medicalHistory;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    @Column(name = "claim_code_hash")
    private String claimCodeHash;

    @Column(name = "claim_code_expires_at")
    private Instant claimCodeExpiresAt;

    @Column(name = "registered_by_user_id")
    private UUID registeredByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Patient() {
    }

    public Patient(UUID userId, String fullName, LocalDate dob, Gender gender, String phone) {
        this.userId = userId;
        this.fullName = fullName;
        this.dob = dob;
        this.gender = gender;
        this.phone = phone;
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

    public int ageOn(LocalDate date) {
        return Period.between(dob, date).getYears();
    }

    public void updateDemographics(String fullName, LocalDate dob, Gender gender, String phone, String address,
                                   String emergencyContactName, String emergencyContactPhone) {
        this.fullName = fullName;
        this.dob = dob;
        this.gender = gender;
        this.phone = phone;
        this.address = address;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public void updateClinical(String knownAllergies, String medicalHistory, String bloodGroup) {
        this.knownAllergies = knownAllergies;
        this.medicalHistory = medicalHistory;
        this.bloodGroup = bloodGroup;
    }

    public void setRegisteredByUserId(UUID registeredByUserId) {
        this.registeredByUserId = registeredByUserId;
    }

    public void issueClaimCode(String hash, Instant expiresAt) {
        this.claimCodeHash = hash;
        this.claimCodeExpiresAt = expiresAt;
    }

    /** Links this record to the patient's own login and invalidates the registration code. */
    public void linkToUser(UUID userId) {
        this.userId = userId;
        this.claimCodeHash = null;
        this.claimCodeExpiresAt = null;
    }

    public UUID getId() {
        return id;
    }

    public String getPatientCode() {
        return patientCode;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDob() {
        return dob;
    }

    public Gender getGender() {
        return gender;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getKnownAllergies() {
        return knownAllergies;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public String getMedicalHistory() {
        return medicalHistory;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public Instant getClaimCodeExpiresAt() {
        return claimCodeExpiresAt;
    }

    public UUID getRegisteredByUserId() {
        return registeredByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
