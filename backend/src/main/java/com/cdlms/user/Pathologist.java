package com.cdlms.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Pathologist profile. Name, qualification and registration number are printed on the report stamp (ADR-011). */
@Entity
@Table(name = "pathologists")
public class Pathologist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String qualification;

    @Column(name = "registration_number", nullable = false, unique = true)
    private String registrationNumber;

    @Column(name = "signature_image_url", columnDefinition = "text")
    private String signatureImageUrl;

    protected Pathologist() {
    }

    public Pathologist(UUID userId, String fullName, String qualification, String registrationNumber) {
        this.userId = userId;
        this.fullName = fullName;
        this.qualification = qualification;
        this.registrationNumber = registrationNumber;
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

    public String getQualification() {
        return qualification;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getSignatureImageUrl() {
        return signatureImageUrl;
    }
}
