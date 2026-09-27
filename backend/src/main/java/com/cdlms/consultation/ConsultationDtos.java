package com.cdlms.consultation;

import com.cdlms.appointment.AppointmentDtos.PatientRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for consultations and prescriptions (Docs/API.md "Consultations & Prescriptions"). */
public final class ConsultationDtos {

    private ConsultationDtos() {
    }

    // ------------------------------------------------------------------ views

    /** Doctor as printed on a prescription. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DoctorCard(UUID id, String fullName, String specialization, String qualification,
                             String registrationNumber) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Vitals(
            @Min(50) @Max(300) Short bpSystolic,
            @Min(20) @Max(200) Short bpDiastolic,
            @Min(20) @Max(250) Short pulse,
            @DecimalMin("30.0") @DecimalMax("45.0") BigDecimal temperatureC,
            @DecimalMin("0.5") @DecimalMax("400.0") BigDecimal weightKg,
            @Min(50) @Max(100) Short spo2) {

        static final Vitals EMPTY = new Vitals(null, null, null, null, null, null);

        Consultation.Vitals toEntity() {
            return new Consultation.Vitals(bpSystolic, bpDiastolic, pulse, temperatureC, weightKg, spo2);
        }

        static Vitals of(Consultation.Vitals v) {
            return new Vitals(v.bpSystolic(), v.bpDiastolic(), v.pulse(), v.temperatureC(), v.weightKg(), v.spo2());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemView(int position, String medicine, String dose, String frequency, Integer durationDays,
                           String instructions) {
    }

    /**
     * {@code supersededBy} is set when a newer prescription replaced this one; the old one stays
     * readable (and downloadable) as a record of what was issued.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PrescriptionView(UUID id, String code, UUID consultationId, UUID appointmentId, Instant issuedAt,
                                   PatientRef patient, DoctorCard doctor, String diagnosis, String advice,
                                   List<ItemView> items, Ref replaces, String revisionReason, Ref supersededBy) {
    }

    public record Ref(UUID id, String code) {
    }

    /** {@code notes} (the doctor's clinical notes) is left out for patients. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConsultationView(UUID id, UUID appointmentId, boolean completed, PatientRef patient,
                                   DoctorCard doctor, String chiefComplaint, String notes, String diagnosis,
                                   String advice, LocalDate followUpDate, Vitals vitals, Instant createdAt,
                                   Instant updatedAt, Instant completedAt, List<PrescriptionView> prescriptions) {
    }

    /** One row in a visit history list. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConsultationSummary(UUID id, UUID appointmentId, Instant date, boolean completed,
                                      PatientRef patient, DoctorCard doctor, String chiefComplaint,
                                      String diagnosis, Ref prescription) {
    }

    public record MedicineSuggestion(String medicine, String dose, String frequency, Integer durationDays,
                                     long timesPrescribed) {
    }

    // ------------------------------------------------------------------ requests

    public record UpdateConsultationRequest(
            @Size(max = 500) String chiefComplaint,
            @Size(max = 20000) String notes,
            @Size(max = 500) String diagnosis,
            @Size(max = 4000) String advice,
            LocalDate followUpDate,
            @Valid Vitals vitals) {
    }

    public record ItemRequest(
            @NotBlank @Size(max = 200) String medicine,
            @Size(max = 100) String dose,
            @NotBlank @Size(max = 60) String frequency,
            @Min(1) @Max(365) Integer durationDays,
            @Size(max = 300) String instructions) {
    }

    public record PrescriptionRequest(
            @Size(max = 4000) String advice,
            @NotEmpty @Size(max = 30) List<@Valid @NotNull ItemRequest> items) {
    }

    /** A correction: the full new prescription plus why the old one was wrong. */
    public record ReviseRequest(
            @NotBlank @Size(max = 300) String reason,
            @Size(max = 4000) String advice,
            @NotEmpty @Size(max = 30) List<@Valid @NotNull ItemRequest> items) {
    }
}
