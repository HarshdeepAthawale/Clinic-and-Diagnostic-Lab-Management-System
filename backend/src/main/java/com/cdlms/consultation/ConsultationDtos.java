package com.cdlms.consultation;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for consultations, prescriptions and the formulary (Docs/API.md). */
public final class ConsultationDtos {

    private ConsultationDtos() {
    }

    public static final int MAX_MEDICINES = 30;

    // ---------------------------------------------------------------- requests

    public record StartRequest(@NotNull UUID appointmentId) {
    }

    public record Vitals(
            @Min(40) @Max(300) Integer bpSystolic,
            @Min(20) @Max(200) Integer bpDiastolic,
            @Min(20) @Max(250) Integer pulseBpm,
            @DecimalMin("30.0") @DecimalMax("45.0") BigDecimal temperatureC,
            @Min(50) @Max(100) Integer spo2Percent,
            @DecimalMin("0.5") @DecimalMax("400.0") BigDecimal weightKg) {

        static final Vitals NONE = new Vitals(null, null, null, null, null, null);
    }

    public record MedicineLine(
            @NotBlank @Size(max = 200) String medicine,
            @Size(max = 100) String dosage,
            @NotBlank @Size(max = 100) String frequency,
            @NotBlank @Size(max = 60) String duration,
            @Size(max = 300) String instructions) {
    }

    /** The whole consultation as the doctor has it now; each save replaces the previous draft. */
    public record ConsultationRequest(
            @Size(max = 500) String chiefComplaint,
            @Size(max = 20_000) String notes,
            @Size(max = 1000) String diagnosis,
            @Size(max = 4000) String advice,
            LocalDate followUpDate,
            @Valid Vitals vitals,
            @Valid @Size(max = MAX_MEDICINES, message = "at most 30 medicines") List<MedicineLine> medicines) {
    }

    // ---------------------------------------------------------------- responses

    public record PatientBrief(UUID id, String patientCode, String fullName, int age, String gender,
                               String knownAllergies, String bloodGroup) {
    }

    public record DoctorBrief(UUID id, String fullName, String specialization, String qualification,
                              String registrationNumber) {
    }

    public record PrescriptionView(UUID id, String prescriptionCode, Instant issuedAt, List<MedicineLine> medicines) {
    }

    /**
     * A consultation. {@code notes} are the doctor's working notes and are left out ({@code null})
     * when the patient views their own consultation.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConsultationView(UUID id, UUID appointmentId, Consultation.Status status, Instant visitAt,
                                   PatientBrief patient, DoctorBrief doctor, String chiefComplaint, String notes,
                                   String diagnosis, String advice, LocalDate followUpDate, Vitals vitals,
                                   PrescriptionView prescription, Instant updatedAt, Instant completedAt) {
    }

    /** One row in a visit history or a doctor's consultation list. */
    public record ConsultationSummary(UUID id, Consultation.Status status, Instant visitAt, UUID patientId,
                                      String patientCode, String patientName, String doctorName, String diagnosis,
                                      UUID prescriptionId, String prescriptionCode, int medicineCount) {
    }

    public record PrescriptionSummary(UUID id, String prescriptionCode, Instant issuedAt, UUID consultationId,
                                      String doctorName, String specialization, String diagnosis, int medicineCount) {
    }

    public record FormularyItem(String name, String form, String defaultStrength) {
    }
}
