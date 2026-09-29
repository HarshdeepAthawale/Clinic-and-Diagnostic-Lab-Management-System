package com.cdlms.result;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SampleResultRepository extends JpaRepository<SampleResult, UUID> {

    List<SampleResult> findBySampleIdOrderByAttemptNumber(UUID sampleId);

    Optional<SampleResult> findFirstBySampleIdAndStatus(UUID sampleId, SampleResult.Status status);

    long countBySampleIdAndStatus(UUID sampleId, SampleResult.Status status);

    int countBySampleId(UUID sampleId);
}
