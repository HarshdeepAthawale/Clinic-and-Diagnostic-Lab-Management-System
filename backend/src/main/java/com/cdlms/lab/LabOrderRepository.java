package com.cdlms.lab;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface LabOrderRepository extends JpaRepository<LabOrder, UUID> {

    Optional<LabOrder> findByConsultationId(UUID consultationId);

    /** Next number in the LO sequence, formatted like {@code LO-000123}. */
    @Query(value = "SELECT 'LO-' || lpad(nextval('lab_order_code_seq')::text, 6, '0')", nativeQuery = true)
    String nextCode();
}
