package com.cdlms.result;

import com.cdlms.lab.LabOrder;
import com.cdlms.lab.TubeType;
import com.cdlms.lab.ValueType;
import com.cdlms.result.RangeCheck.Flag;
import com.cdlms.sample.RejectionRecord;
import com.cdlms.sample.SampleStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for results, verification and reports (Docs/API.md "Samples" and "Reports"). */
public final class ResultDtos {

    private ResultDtos() {
    }

    // ---------------------------------------------------------------- requests

    /** One typed value: the parameter and what was read (a number or text, sent as a string). */
    public record ValueEntry(@NotNull UUID parameterId, @NotBlank @Size(max = 200) String value) {
    }

    /** All the values of one attempt — every parameter of every test on the sample. */
    public record EnterResultsRequest(@Size(max = 60) String analyzer, @NotEmpty @Valid List<ValueEntry> values) {
    }

    public record ReturnRequest(@NotNull SampleResult.ReturnReason reason, @Size(max = 500) String note) {
    }

    /** Rejection during testing: sample used up, degraded, or something else (with a note). */
    public record RejectRequest(@NotNull RejectionRecord.Reason reason, @Size(max = 500) String note) {
    }

    public record DispatchRequest(@NotNull Report.Channel channel) {
    }

    // ---------------------------------------------------------------- what to enter

    public record ParameterSpec(UUID parameterId, int position, String name, String unit, ValueType valueType,
                                BigDecimal refLow, BigDecimal refHigh, BigDecimal criticalLow, BigDecimal criticalHigh) {
    }

    /** One test on the sample with the parameters to fill in. */
    public record TestSheet(UUID itemId, String testCode, String testName, List<ParameterSpec> parameters) {
    }

    // ---------------------------------------------------------------- what was entered

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ValueView(UUID itemId, UUID parameterId, int position, String name, String unit, ValueType valueType,
                            BigDecimal numericValue, String textValue, BigDecimal refLow, BigDecimal refHigh,
                            BigDecimal criticalLow, BigDecimal criticalHigh, Flag flag) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Attempt(UUID id, int attemptNumber, SampleResult.Status status, String analyzer, String enteredBy,
                          Instant enteredAt, String verifiedBy, Instant verifiedAt, String returnedBy, Instant returnedAt,
                          SampleResult.ReturnReason returnReason, String returnNote, boolean critical,
                          List<ValueView> values) {
    }

    /** Earlier verified values of the same parameter for the same patient, newest first — the trend behind a value. */
    public record TrendPoint(BigDecimal value, Instant verifiedAt) {
    }

    /**
     * Everything the bench and the pathologist need for one sample: what to fill in, every attempt so far
     * (returned ones included), and — for the pathologist — the patient's earlier values.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SampleResults(UUID sampleId, String sampleCode, SampleStatus status, TubeType tubeType,
                                UUID labOrderId, String orderCode, LabOrder.Priority priority, Patient patient,
                                Instant collectedAt, Instant receivedAt, List<TestSheet> sheet, List<Attempt> attempts,
                                int retestCount, SampleResult.ReturnReason lastReturnReason, String lastReturnNote,
                                java.util.Map<UUID, List<TrendPoint>> trend) {
    }

    public record Patient(UUID id, String patientCode, String fullName, int age, String gender) {
    }

    // ---------------------------------------------------------------- lists

    /** A row in the lab's "to test" list; retests are pinned first with the pathologist's reason. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TestingRow(UUID id, String sampleCode, SampleStatus status, TubeType tubeType, UUID labOrderId,
                             String orderCode, LabOrder.Priority priority, UUID patientId, String patientName,
                             List<String> testNames, Instant receivedAt, int retestCount,
                             SampleResult.ReturnReason returnReason, String returnNote) {
    }

    /** A row in the pathologist's queue or history. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VerificationRow(UUID sampleId, String sampleCode, UUID labOrderId, String orderCode, UUID patientId,
                                  String patientName, List<String> testNames, int attemptNumber, Instant enteredAt,
                                  Instant verifiedAt, boolean critical, int abnormalCount) {
    }

    // ---------------------------------------------------------------- reports

    public record Verifier(String name, String qualification, String registrationNumber) {
    }

    /** One test's rows on the report. */
    public record ReportTest(String testCode, String testName, List<ValueView> values) {
    }

    /** The report itself: what the reader view and the PDF show. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReportView(UUID sampleId, String sampleCode, UUID labOrderId, String orderCode, Patient patient,
                             String orderingDoctor, TubeType tubeType, Instant collectedAt, Instant receivedAt,
                             int attemptNumber, List<ReportTest> tests, Verifier verifier, Instant verifiedAt,
                             Instant generatedAt, Report.Channel dispatchedChannel, Instant dispatchedAt,
                             Instant receiptConfirmedAt, boolean critical, String verificationCode) {
    }

    /**
     * What anyone holding a printed report sees after scanning its QR code (ADR-027): that the clinic issued it,
     * when, which tests, and who verified it — never any results. The patient appears as initials only.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReportAuthenticity(boolean authentic, String clinicName, String reportNumber, String patientInitials,
                                     List<String> tests, Instant verifiedAt, Verifier verifier) {
    }

    /** A row in a list of reports. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReportRow(UUID sampleId, String sampleCode, UUID labOrderId, String orderCode, UUID patientId,
                            String patientCode, String patientName, List<String> testNames, Instant verifiedAt,
                            Instant dispatchedAt, Report.Channel dispatchedChannel, Instant receiptConfirmedAt,
                            boolean critical, boolean abnormal) {
    }
}
