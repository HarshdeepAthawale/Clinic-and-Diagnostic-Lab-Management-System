package com.cdlms.sample;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RejectionRecordRepository extends JpaRepository<RejectionRecord, UUID> {

    Optional<RejectionRecord> findBySampleId(UUID sampleId);
}
