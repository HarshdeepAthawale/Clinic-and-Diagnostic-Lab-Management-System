package com.cdlms.consultation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    /** An original (not a revision) already issued for this consultation. */
    boolean existsByConsultationIdAndReplacesIdIsNull(UUID consultationId);

    /** Whether a newer prescription already replaces this one. */
    boolean existsByReplacesId(UUID replacesId);
}
