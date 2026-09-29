package com.cdlms.sample;

import com.cdlms.lab.LabOrder;
import com.cdlms.lab.TubeType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for samples and notifications (Docs/API.md "Samples"). */
public final class SampleDtos {

    private SampleDtos() {
    }

    // ---------------------------------------------------------------- requests

    /**
     * Records the draw. If the tube is not the one the tests need, the request is refused with
     * {@code TUBE_MISMATCH} unless {@code confirmMismatch} is set — a mismatch is never accepted silently.
     */
    public record CollectRequest(
            @NotNull TubeType tubeTypeUsed,
            @Size(max = 60) String bodySite,
            boolean confirmMismatch) {
    }

    /** The receipt check: accept, or reject with a reason (a note is needed for {@code OTHER}). */
    public record ReceiveRequest(
            @NotNull Boolean accepted,
            RejectionRecord.Reason reason,
            @Size(max = 500) String note) {
    }

    // ---------------------------------------------------------------- responses

    public record SamplePatient(UUID id, String patientCode, String fullName, int age, String gender) {
    }

    public record SampleTest(UUID itemId, String code, String name) {
    }

    /** One step of the journey. Staff names are left out ({@code null}) for patients and doctors. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SampleEvent(SampleStatus status, Instant occurredAt, String actorName, String actorRole, String detail) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Rejection(RejectionRecord.Stage stage, RejectionRecord.Reason reason, String note, Instant flaggedAt,
                            String flaggedBy) {
    }

    /**
     * One sample in full. {@code rejection} is only present for lab staff; patients just see the status
     * and a kind message. {@code redrawSampleCode} names the sample that replaced a rejected one.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SampleView(UUID id, String sampleCode, SampleStatus status, TubeType requiredTubeType,
                             TubeType tubeTypeUsed, boolean tubeMismatch, String bodySite, Instant createdAt,
                             Instant collectedAt, Instant receivedAt, UUID labOrderId, String orderCode,
                             LabOrder.Priority priority, SamplePatient patient, List<SampleTest> tests,
                             String redrawOfSampleCode, UUID redrawSampleId, String redrawSampleCode,
                             Rejection rejection, List<SampleEvent> events) {
    }

    /** A row in the lab's sample lists. */
    public record SampleSummary(UUID id, String sampleCode, SampleStatus status, TubeType requiredTubeType,
                                boolean tubeMismatch, boolean redraw, Instant createdAt, Instant collectedAt,
                                UUID labOrderId, String orderCode, LabOrder.Priority priority, UUID patientId,
                                String patientCode, String patientName, List<String> testNames) {
    }

    public record NotificationView(UUID id, Notification.Type type, String title, String message, UUID patientId,
                                   UUID sampleId, Instant createdAt) {
    }

    /** The front desk's inbox: open items, newest first, plus how many are open in all. */
    public record NotificationList(List<NotificationView> items, long open) {
    }
}
