package com.cdlms.appointment;

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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A booked slot or a walk-in token with one doctor (Docs/Schema.md §2). */
@Entity
@Table(name = "appointments")
public class Appointment {

    public enum Kind { SCHEDULED, WALK_IN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private UUID doctorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Kind kind;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private short durationMinutes;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "queue_date")
    private LocalDate queueDate;

    @Column(name = "queue_number")
    private Integer queueNumber;

    @Column(name = "queue_token")
    private String queueToken;

    @Column(name = "booked_by_user_id", updatable = false)
    private UUID bookedByUserId;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appointment() {
    }

    private Appointment(UUID patientId, UUID doctorId, Kind kind, Instant scheduledAt, int durationMinutes,
                        String reason, AppointmentStatus status, UUID bookedByUserId) {
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.kind = kind;
        this.scheduledAt = scheduledAt;
        this.durationMinutes = (short) durationMinutes;
        this.reason = reason;
        this.status = status;
        this.bookedByUserId = bookedByUserId;
    }

    public static Appointment booked(UUID patientId, UUID doctorId, Instant slot, int slotMinutes, String reason,
                                     UUID bookedBy) {
        return new Appointment(patientId, doctorId, Kind.SCHEDULED, slot, slotMinutes, reason,
                AppointmentStatus.BOOKED, bookedBy);
    }

    /** A walk-in is checked in the moment it's created, so it joins the queue straight away. */
    public static Appointment walkIn(UUID patientId, UUID doctorId, Instant now, String reason, UUID issuedBy,
                                     LocalDate queueDate, int queueNumber) {
        Appointment a = new Appointment(patientId, doctorId, Kind.WALK_IN, now, 15, reason,
                AppointmentStatus.CHECKED_IN, issuedBy);
        a.assignToken(queueDate, queueNumber);
        a.checkedInAt = now;
        return a;
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

    /** Token numbers restart every clinic day: T-001, T-002, … */
    public static String formatToken(int number) {
        return "T-%03d".formatted(number);
    }

    void assignToken(LocalDate date, int number) {
        this.queueDate = date;
        this.queueNumber = number;
        this.queueToken = formatToken(number);
    }

    /** Applies a status change the service has already authorised, stamping the matching time. */
    void moveTo(AppointmentStatus target, Instant at, String note) {
        switch (target) {
            case CHECKED_IN -> checkedInAt = at;
            case IN_CONSULTATION -> startedAt = at;
            case COMPLETED -> completedAt = at;
            case CANCELLED -> {
                cancelledAt = at;
                cancellationReason = note;
            }
            default -> {
            }
        }
        this.status = target;
    }

    void markReminderSent(Instant at) {
        this.reminderSentAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public Kind getKind() {
        return kind;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getReason() {
        return reason;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public LocalDate getQueueDate() {
        return queueDate;
    }

    public Integer getQueueNumber() {
        return queueNumber;
    }

    public String getQueueToken() {
        return queueToken;
    }

    public UUID getBookedByUserId() {
        return bookedByUserId;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getReminderSentAt() {
        return reminderSentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
