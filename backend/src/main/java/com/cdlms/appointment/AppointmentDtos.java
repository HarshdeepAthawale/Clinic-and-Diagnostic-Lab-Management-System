package com.cdlms.appointment;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for appointments, queue and doctor working hours (Docs/API.md "Appointments"). */
public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    // ------------------------------------------------------------------ views

    public record PatientRef(UUID id, String patientCode, String fullName, int age, String gender) {
    }

    public record DoctorRef(UUID id, String fullName, String specialization) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppointmentView(UUID id, Appointment.Kind kind, AppointmentStatus status, Instant scheduledAt,
                                  int durationMinutes, String reason, String queueToken, PatientRef patient,
                                  DoctorRef doctor, Instant checkedInAt, Instant startedAt, Instant completedAt,
                                  Instant cancelledAt, String cancellationReason, Instant createdAt) {
    }

    public record EventView(AppointmentStatus fromStatus, AppointmentStatus toStatus, String changedBy, String note,
                            Instant at) {
    }

    public record AppointmentDetail(AppointmentView appointment, List<EventView> history) {
    }

    public record DoctorOption(UUID id, String fullName, String specialization, boolean hasWorkingHours) {
    }

    public record Slot(Instant startsAt, boolean available) {
    }

    /** {@code working} is false when the doctor has no hours that day (then {@code slots} is empty). */
    public record DaySlots(UUID doctorId, LocalDate date, boolean working, int slotMinutes, List<Slot> slots) {
    }

    /** One doctor's column on the live queue board. */
    public record QueueColumn(DoctorRef doctor, AppointmentView nowServing, List<AppointmentView> waiting,
                              long seen, long noShows) {
    }

    public record QueueBoard(LocalDate date, Instant generatedAt, List<QueueColumn> doctors) {
    }

    // ------------------------------------------------------------------ requests

    /** {@code patientId} is required for the front desk and ignored for patients (they book for themselves). */
    public record BookRequest(
            @NotNull UUID doctorId,
            @NotNull Instant scheduledAt,
            UUID patientId,
            @Size(max = 500) String reason) {
    }

    public record TokenRequest(
            @NotNull UUID patientId,
            @NotNull UUID doctorId,
            @Size(max = 500) String reason) {
    }

    public record StatusRequest(
            @NotNull AppointmentStatus status,
            @Size(max = 300) String note) {
    }

    public record WorkingBlock(
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @Min(5) @Max(120) Integer slotMinutes) {
    }

    public record WorkingHours(@NotNull @Size(max = 28) List<@Valid @NotNull WorkingBlock> blocks) {
    }
}
