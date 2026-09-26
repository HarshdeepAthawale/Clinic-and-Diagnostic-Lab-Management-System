package com.cdlms.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PatientAccessLogRepository extends JpaRepository<PatientAccessLog, UUID> {

    /** A log row joined with who (name, role) and which patient (name, code), for the admin view. */
    interface Row {
        UUID getId();

        Instant getAccessedAt();

        String getResource();

        UUID getPatientId();

        String getPatientCode();

        String getPatientName();

        UUID getUserId();

        String getUserRole();

        String getUserName();
    }

    @Query(value = """
            SELECT l.id AS id, l.accessed_at AS accessedAt, l.resource AS resource,
                   p.id AS patientId, p.patient_code AS patientCode, p.full_name AS patientName,
                   u.id AS userId, u.role AS userRole,
                   COALESCE(d.full_name, pa.full_name, s.full_name, u.email) AS userName
            FROM patient_access_log l
            JOIN patients p ON p.id = l.patient_id
            JOIN users u ON u.id = l.user_id
            LEFT JOIN doctors d ON d.user_id = u.id
            LEFT JOIN pathologists pa ON pa.user_id = u.id
            LEFT JOIN staff s ON s.user_id = u.id
            WHERE (CAST(:patientId AS uuid) IS NULL OR l.patient_id = CAST(:patientId AS uuid))
              AND (CAST(:userId AS uuid) IS NULL OR l.user_id = CAST(:userId AS uuid))
              AND (CAST(:from AS timestamptz) IS NULL OR l.accessed_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR l.accessed_at < CAST(:to AS timestamptz))
            ORDER BY l.accessed_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM patient_access_log l
            WHERE (CAST(:patientId AS uuid) IS NULL OR l.patient_id = CAST(:patientId AS uuid))
              AND (CAST(:userId AS uuid) IS NULL OR l.user_id = CAST(:userId AS uuid))
              AND (CAST(:from AS timestamptz) IS NULL OR l.accessed_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR l.accessed_at < CAST(:to AS timestamptz))
            """,
            nativeQuery = true)
    Page<Row> search(@Param("patientId") UUID patientId, @Param("userId") UUID userId,
                     @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
