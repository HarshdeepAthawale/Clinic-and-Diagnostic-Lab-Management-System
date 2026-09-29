package com.cdlms.sample;

import com.cdlms.lab.TubeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SampleRepository extends JpaRepository<Sample, UUID> {

    Optional<Sample> findBySampleCodeIgnoreCase(String sampleCode);

    List<Sample> findByLabOrderIdOrderByCreatedAt(UUID labOrderId);

    List<Sample> findTop50ByPatientIdOrderByCreatedAtDesc(UUID patientId);

    /** The order's not-yet-collected sample for this tube — where a newly ordered test can join. */
    Optional<Sample> findFirstByLabOrderIdAndRequiredTubeTypeAndStatus(UUID labOrderId, TubeType tube, SampleStatus status);

    /** The sample that will actually process an order line: the one that isn't rejected or cancelled. */
    @Query("""
            SELECT s FROM Sample s JOIN s.itemIds i
            WHERE i = :itemId AND s.status NOT IN (com.cdlms.sample.SampleStatus.REJECTED, com.cdlms.sample.SampleStatus.CANCELLED)
            """)
    Optional<Sample> findLiveByItemId(@Param("itemId") UUID itemId);

    @Query("""
            SELECT s FROM Sample s JOIN s.itemIds i
            WHERE i IN :itemIds AND s.status NOT IN (com.cdlms.sample.SampleStatus.REJECTED, com.cdlms.sample.SampleStatus.CANCELLED)
            """)
    List<Sample> findLiveByItemIds(@Param("itemIds") Collection<UUID> itemIds);

    Optional<Sample> findByRedrawOfSampleId(UUID rejectedSampleId);
}
