package com.cdlms.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/** One weekly block of a doctor's working hours, cut into bookable slots. */
@Entity
@Table(name = "doctor_schedules")
public class DoctorSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private UUID doctorId;

    /** ISO day of week: 1 = Monday … 7 = Sunday. */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_minutes", nullable = false)
    private short slotMinutes;

    protected DoctorSchedule() {
    }

    public DoctorSchedule(UUID doctorId, DayOfWeek day, LocalTime startTime, LocalTime endTime, int slotMinutes) {
        this.doctorId = doctorId;
        this.dayOfWeek = (short) day.getValue();
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotMinutes = (short) slotMinutes;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public DayOfWeek getDayOfWeek() {
        return DayOfWeek.of(dayOfWeek);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getSlotMinutes() {
        return slotMinutes;
    }
}
