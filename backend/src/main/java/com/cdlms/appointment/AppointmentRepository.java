package com.cdlms.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    boolean existsByDoctorIdAndStatus(UUID doctorId, AppointmentStatus status);

    /** Next token number for the clinic day; callers hold the day's advisory lock (see AppointmentService). */
    @Query("SELECT COALESCE(MAX(a.queueNumber), 0) + 1 FROM Appointment a WHERE a.queueDate = :date")
    int nextQueueNumber(@Param("date") LocalDate date);

    /** Live bookings of one patient with one doctor inside a window (to stop duplicate same-day bookings). */
    @Query("""
            SELECT count(a) > 0 FROM Appointment a
            WHERE a.patientId = :patientId AND a.doctorId = :doctorId
              AND a.scheduledAt >= :from AND a.scheduledAt < :to
              AND a.status IN (com.cdlms.appointment.AppointmentStatus.BOOKED,
                               com.cdlms.appointment.AppointmentStatus.CHECKED_IN,
                               com.cdlms.appointment.AppointmentStatus.IN_CONSULTATION)
            """)
    boolean hasLiveBooking(@Param("patientId") UUID patientId, @Param("doctorId") UUID doctorId,
                           @Param("from") Instant from, @Param("to") Instant to);

    /** Serialises token numbering for one clinic day across concurrent requests. */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext('queue:' || CAST(:date AS text)))",
            nativeQuery = true)
    int lockQueueDay(@Param("date") LocalDate date);
}
