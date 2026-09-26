package com.cdlms.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppointmentEventRepository extends JpaRepository<AppointmentEvent, UUID> {

    List<AppointmentEvent> findByAppointmentIdOrderByCreatedAt(UUID appointmentId);
}
