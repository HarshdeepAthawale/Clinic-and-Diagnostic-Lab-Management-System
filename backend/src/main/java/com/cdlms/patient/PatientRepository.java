package com.cdlms.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByUserId(UUID userId);

    Optional<Patient> findByClaimCodeHash(String claimCodeHash);

    /**
     * Search by name (contains, case-insensitive), exact patient code, or phone digits (contains).
     * {@code digits} is empty when the query has no digits, so the phone clause never matches then.
     */
    @Query(value = """
            SELECT * FROM patients p
            WHERE lower(p.full_name) LIKE :namePattern
               OR p.patient_code = :code
               OR (:digits <> '' AND p.phone_digits LIKE :digitsPattern)
            ORDER BY p.full_name
            """,
            countQuery = """
            SELECT count(*) FROM patients p
            WHERE lower(p.full_name) LIKE :namePattern
               OR p.patient_code = :code
               OR (:digits <> '' AND p.phone_digits LIKE :digitsPattern)
            """,
            nativeQuery = true)
    Page<Patient> search(@Param("namePattern") String namePattern, @Param("code") String code,
                         @Param("digits") String digits, @Param("digitsPattern") String digitsPattern,
                         Pageable pageable);

    Page<Patient> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Loads a patient only if the doctor has a care relationship with them (ADR-015): a
     * non-cancelled appointment with that doctor, or a consultation by them. The check runs inside
     * the query, so a record is never loaded for a doctor who may not see it (Security.md §6).
     */
    @Query(value = """
            SELECT p.* FROM patients p
            WHERE p.id = :patientId
              AND (EXISTS (SELECT 1 FROM appointments a
                           WHERE a.patient_id = p.id AND a.doctor_id = :doctorId AND a.status <> 'CANCELLED')
                   OR EXISTS (SELECT 1 FROM consultations c WHERE c.patient_id = p.id AND c.doctor_id = :doctorId))
            """, nativeQuery = true)
    Optional<Patient> findWithCareRelationship(@Param("patientId") UUID patientId, @Param("doctorId") UUID doctorId);

    /** Same rule as {@link #findWithCareRelationship}, without loading the record. */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM appointments a
                           WHERE a.patient_id = :patientId AND a.doctor_id = :doctorId AND a.status <> 'CANCELLED')
                OR EXISTS (SELECT 1 FROM consultations c WHERE c.patient_id = :patientId AND c.doctor_id = :doctorId)
            """, nativeQuery = true)
    boolean hasCareRelationship(@Param("patientId") UUID patientId, @Param("doctorId") UUID doctorId);

    /** Which of the given patients the doctor has a care relationship with (for search results). */
    @Query(value = """
            SELECT a.patient_id FROM appointments a
            WHERE a.doctor_id = :doctorId AND a.status <> 'CANCELLED' AND a.patient_id IN (:patientIds)
            UNION
            SELECT c.patient_id FROM consultations c
            WHERE c.doctor_id = :doctorId AND c.patient_id IN (:patientIds)
            """, nativeQuery = true)
    List<UUID> careRelationshipsAmong(@Param("doctorId") UUID doctorId,
                                      @Param("patientIds") Collection<UUID> patientIds);
}
