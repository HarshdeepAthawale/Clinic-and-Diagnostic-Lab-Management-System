package com.cdlms.result;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findBySampleId(UUID sampleId);

    /**
     * Records that the patient opened the report, once. A single conditional UPDATE, so two requests
     * arriving together (two tabs, two devices) can't both write it: the second changes nothing.
     * Returns 1 when this call was the first open.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Report r SET r.receiptConfirmedAt = :at "
            + "WHERE r.sampleId = :sampleId AND r.dispatchedAt IS NOT NULL AND r.receiptConfirmedAt IS NULL")
    int confirmReceipt(@Param("sampleId") UUID sampleId, @Param("at") Instant at);
}
