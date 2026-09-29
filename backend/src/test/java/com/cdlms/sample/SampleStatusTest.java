package com.cdlms.sample;

import com.cdlms.lab.TubeType;
import com.cdlms.sample.RejectionRecord.Reason;
import com.cdlms.sample.RejectionRecord.Stage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The sample state machine and code format, without a database (Rules.md §2). */
class SampleStatusTest {

    private static Sample newSample() {
        return new Sample("LAB-20260929-0001", UUID.randomUUID(), UUID.randomUUID(), TubeType.EDTA, null);
    }

    @Test
    void movesForwardThroughTheReceiptCheck() {
        Sample sample = newSample();
        UUID tech = UUID.randomUUID();

        sample.collect(TubeType.EDTA, false, "Left arm", tech, Instant.now());
        assertThat(sample.getStatus()).isEqualTo(SampleStatus.COLLECTED);
        sample.receive(tech, Instant.now());
        assertThat(sample.getStatus()).isEqualTo(SampleStatus.RECEIVED_AT_LAB);
    }

    @Test
    void cannotSkipStagesOrGoBackwards() {
        Sample sample = newSample();

        assertThatThrownBy(() -> sample.receive(UUID.randomUUID(), Instant.now())).isInstanceOf(IllegalStateException.class);
        sample.collect(TubeType.EDTA, false, null, UUID.randomUUID(), Instant.now());
        assertThatThrownBy(() -> sample.collect(TubeType.EDTA, false, null, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(SampleStatus.RECEIVED_AT_LAB.canMoveTo(SampleStatus.COLLECTED)).isFalse();
        assertThat(SampleStatus.VERIFIED.canMoveTo(SampleStatus.IN_TESTING)).isFalse();
    }

    @Test
    void onlyTwoBackwardOrSidewaysPathsExist() {
        // Return for retest keeps the same sample; rejection ends it. Nothing else leaves the pipeline.
        assertThat(SampleStatus.RESULT_ENTERED.canMoveTo(SampleStatus.IN_TESTING)).isTrue();
        assertThat(SampleStatus.COLLECTED.canMoveTo(SampleStatus.REJECTED)).isTrue();
        assertThat(SampleStatus.IN_TESTING.canMoveTo(SampleStatus.REJECTED)).isTrue();
        assertThat(SampleStatus.RECEIVED_AT_LAB.canMoveTo(SampleStatus.REJECTED)).isFalse();
        assertThat(SampleStatus.VERIFIED.canMoveTo(SampleStatus.REJECTED)).isFalse();
    }

    @Test
    void rejectedAndCancelledSamplesAreFinal() {
        for (SampleStatus end : new SampleStatus[] {SampleStatus.REJECTED, SampleStatus.CANCELLED, SampleStatus.DISPATCHED}) {
            assertThat(end.next()).isEmpty();
        }
        assertThat(SampleStatus.REJECTED.isLive()).isFalse();
        assertThat(SampleStatus.CANCELLED.isLive()).isFalse();
        assertThat(SampleStatus.COLLECTED.isLive()).isTrue();
    }

    @Test
    void anUncollectedSampleCanBeCancelledButACollectedOneCannot() {
        Sample sample = newSample();
        sample.collect(TubeType.EDTA, false, null, UUID.randomUUID(), Instant.now());

        assertThatThrownBy(sample::cancel).isInstanceOf(IllegalStateException.class);
        Sample fresh = newSample();
        fresh.cancel();
        assertThat(fresh.getStatus()).isEqualTo(SampleStatus.CANCELLED);
    }

    @Test
    void rejectionReasonsDependOnTheStage() {
        assertThat(Reason.HEMOLYZED.allowedAt(Stage.RECEIVED_AT_LAB)).isTrue();
        assertThat(Reason.HEMOLYZED.allowedAt(Stage.IN_TESTING)).isFalse();
        assertThat(Reason.SAMPLE_EXHAUSTED.allowedAt(Stage.IN_TESTING)).isTrue();
        assertThat(Reason.SAMPLE_EXHAUSTED.allowedAt(Stage.RECEIVED_AT_LAB)).isFalse();
        assertThat(Reason.OTHER.allowedAt(Stage.RECEIVED_AT_LAB)).isTrue();
        assertThat(Reason.OTHER.allowedAt(Stage.IN_TESTING)).isTrue();
    }

    @Test
    void sampleCodesUseTheDateAndAFourDigitCounter() {
        assertThat(SampleCodes.format(LocalDate.of(2026, 9, 29), 7)).isEqualTo("LAB-20260929-0007");
        assertThat(SampleCodes.format(LocalDate.of(2026, 1, 5), 1234)).isEqualTo("LAB-20260105-1234");
        assertThat(SampleCodes.format(LocalDate.of(2026, 9, 29), 10001)).isEqualTo("LAB-20260929-10001");
    }

    @Test
    void bloodTubesNeedABodySiteButCupsDoNot() {
        assertThat(TubeType.EDTA.isBlood()).isTrue();
        assertThat(TubeType.FLUORIDE.isBlood()).isTrue();
        assertThat(TubeType.URINE_CUP.isBlood()).isFalse();
        assertThat(TubeType.SWAB_TUBE.isBlood()).isFalse();
    }
}
