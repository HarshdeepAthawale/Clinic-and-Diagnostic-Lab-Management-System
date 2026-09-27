package com.cdlms.consultation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    Optional<Prescription> findByConsultationId(UUID consultationId);

    /** Next number in the RX sequence, formatted like {@code RX-000123}. */
    @Query(value = "SELECT 'RX-' || lpad(nextval('prescription_code_seq')::text, 6, '0')", nativeQuery = true)
    String nextCode();
}
