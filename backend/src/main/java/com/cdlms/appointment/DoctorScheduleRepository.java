package com.cdlms.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, UUID> {

    @Query("SELECT s FROM DoctorSchedule s WHERE s.doctorId = :doctorId ORDER BY s.dayOfWeek, s.startTime")
    List<DoctorSchedule> findForDoctor(@Param("doctorId") UUID doctorId);

    @Query("SELECT s FROM DoctorSchedule s WHERE s.doctorId = :doctorId AND s.dayOfWeek = :day ORDER BY s.startTime")
    List<DoctorSchedule> findForDay(@Param("doctorId") UUID doctorId, @Param("day") short isoDay);

    @Modifying
    @Query("DELETE FROM DoctorSchedule s WHERE s.doctorId = :doctorId")
    void deleteForDoctor(@Param("doctorId") UUID doctorId);
}
