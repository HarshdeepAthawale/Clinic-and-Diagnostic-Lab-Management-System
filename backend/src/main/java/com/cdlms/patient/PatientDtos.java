package com.cdlms.patient;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Request/response bodies for {@code /api/patients} (Docs/API.md "Patients"). */
public final class PatientDtos {

    private PatientDtos() {
    }

    static final String PHONE_REGEX = "^\\+?[0-9 ()-]{7,20}$";
    static final String BLOOD_GROUP_REGEX = "^(A|B|AB|O)[+-]$";

    /**
     * Search result: basic details only, phone masked (ADR-015). {@code hasCareRelationship} is only
     * set for doctors.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PatientSummary(UUID id, String patientCode, String fullName, int age, Gender gender,
                                 String maskedPhone, Boolean hasCareRelationship) {
    }

    /** Demographics and contact, no clinical fields — for the patient, front desk and admin. */
    public record PatientDetails(UUID id, String patientCode, String fullName, LocalDate dob, int age,
                                 Gender gender, String phone, String address, String emergencyContactName,
                                 String emergencyContactPhone, boolean hasLogin, Instant registrationCodeExpiresAt,
                                 Instant createdAt) {
    }

    /** The full medical record (EMR) — for the patient themself and doctors with a care relationship. */
    public record PatientRecord(UUID id, String patientCode, String fullName, LocalDate dob, int age, Gender gender,
                                String phone, String address, String emergencyContactName,
                                String emergencyContactPhone, String bloodGroup, String knownAllergies,
                                String medicalHistory, Instant updatedAt) {
    }

    /** Front-desk registration. Clinical fields are optional at intake. */
    public record RegisterPatientRequest(
            @NotBlank @Size(max = 200) String fullName,
            @NotNull @Past LocalDate dob,
            @NotNull Gender gender,
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "must be a valid phone number") String phone,
            @Size(max = 500) String address,
            @Size(max = 2000) String knownAllergies,
            @Pattern(regexp = BLOOD_GROUP_REGEX, message = "must be like A+, O- or AB+") String bloodGroup,
            @Size(max = 4000) String medicalHistory,
            @Size(max = 200) String emergencyContactName,
            @Pattern(regexp = PHONE_REGEX, message = "must be a valid phone number") String emergencyContactPhone) {
    }

    /**
     * Returned once, right after registration: the only time the plain registration code is ever
     * available (only its hash is stored).
     */
    public record RegisteredPatient(PatientDetails patient, String registrationCode, Instant registrationCodeExpiresAt) {
    }

    public record UpdateDemographicsRequest(
            @NotBlank @Size(max = 200) String fullName,
            @NotNull @Past LocalDate dob,
            @NotNull Gender gender,
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "must be a valid phone number") String phone,
            @Size(max = 500) String address,
            @Size(max = 200) String emergencyContactName,
            @Pattern(regexp = PHONE_REGEX, message = "must be a valid phone number") String emergencyContactPhone) {
    }

    public record UpdateClinicalRequest(
            @Size(max = 2000) String knownAllergies,
            @Size(max = 4000) String medicalHistory,
            @Pattern(regexp = BLOOD_GROUP_REGEX, message = "must be like A+, O- or AB+") String bloodGroup) {
    }

    public record AccessLogEntry(UUID id, Instant accessedAt, String resource, UUID patientId, String patientCode,
                                 String patientName, UUID userId, String userRole, String userName) {
    }
}
