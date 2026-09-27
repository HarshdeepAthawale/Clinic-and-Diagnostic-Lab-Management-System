package com.cdlms.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "doctors")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String specialization;

    /** e.g. "MBBS, MD (Medicine)" — printed on prescriptions. */
    private String qualification;

    /** Medical council registration, printed on prescriptions. */
    @Column(name = "registration_number")
    private String registrationNumber;

    protected Doctor() {
    }

    public Doctor(UUID userId, String fullName, String specialization) {
        this.userId = userId;
        this.fullName = fullName;
        this.specialization = specialization;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getSpecialization() {
        return specialization;
    }

    public String getQualification() {
        return qualification;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }
}
