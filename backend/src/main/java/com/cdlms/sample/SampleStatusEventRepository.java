package com.cdlms.sample;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SampleStatusEventRepository extends JpaRepository<SampleStatusEvent, UUID> {

    List<SampleStatusEvent> findBySampleIdOrderByOccurredAt(UUID sampleId);
}
