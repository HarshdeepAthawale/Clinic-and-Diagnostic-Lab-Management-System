package com.cdlms.lab;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabOrderRepository extends JpaRepository<LabOrder, UUID> {

    Optional<LabOrder> findByConsultationIdAndStatus(UUID consultationId, LabOrder.Status status);

    List<LabOrder> findTop30ByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<LabOrder> findTop5ByPatientIdAndStatusOrderByCreatedAtDesc(UUID patientId, LabOrder.Status status);

    /** Next number in the LO sequence, formatted like {@code LO-000123}. */
    @Query(value = "SELECT 'LO-' || lpad(nextval('lab_order_code_seq')::text, 6, '0')", nativeQuery = true)
    String nextCode();
}
