package com.cdlms.lab;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for the lab test catalog and lab orders (Docs/API.md "Lab Tests & Orders"). */
public final class LabDtos {

    private LabDtos() {
    }

    public static final int MAX_TESTS_PER_ORDER = 25;

    // ---------------------------------------------------------------- catalog

    public record ParameterRange(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 30) String unit,
            BigDecimal refLow,
            BigDecimal refHigh,
            BigDecimal criticalLow,
            BigDecimal criticalHigh) {
    }

    /** Admin create/update. {@code code} is only read on create; codes never change once orders use them. */
    public record LabTestRequest(
            @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9-]*", message = "letters, digits and dashes only") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 40) String category,
            @NotNull SampleType sampleType,
            @NotNull TubeType requiredTubeType,
            @NotNull @DecimalMin("0.00") @DecimalMax("999999.99") BigDecimal price,
            @NotNull @Min(1) @Max(720) Integer turnaroundHours,
            @Size(max = 500) String prepInstructions,
            Boolean active,
            @Valid @Size(max = 40) List<ParameterRange> parameters) {
    }

    public record LabTestSummary(UUID id, String code, String name, String category, SampleType sampleType,
                                 TubeType requiredTubeType, BigDecimal price, int turnaroundHours,
                                 String prepInstructions, boolean active, int parameterCount) {
    }

    public record LabTestView(UUID id, String code, String name, String category, SampleType sampleType,
                              TubeType requiredTubeType, BigDecimal price, int turnaroundHours,
                              String prepInstructions, boolean active, List<ParameterRange> parameters,
                              Instant updatedAt) {
    }

    // ---------------------------------------------------------------- orders

    /**
     * A doctor's order. From a consultation, only {@code consultationId} identifies the patient; a
     * direct order names the patient. Ordering again in the same consultation adds to its open order.
     */
    public record OrderRequest(
            UUID consultationId,
            UUID patientId,
            @NotEmpty @Size(max = MAX_TESTS_PER_ORDER, message = "at most 25 tests per order") List<@NotNull UUID> testIds,
            LabOrder.Priority priority,
            @Size(max = 500) String clinicalNotes) {
    }

    public record CancelRequest(@Size(max = 300) String reason) {
    }

    public record OrderPatient(UUID id, String patientCode, String fullName, int age, String gender) {
    }

    public record OrderDoctor(UUID id, String fullName, String specialization) {
    }

    public record OrderItemView(UUID id, UUID testId, String code, String name, String category,
                                SampleType sampleType, TubeType tubeType, BigDecimal price, int turnaroundHours,
                                String prepInstructions, LabOrderItem.Status status) {
    }

    /** One order with its tests. {@code clinicalNotes} (for the lab) are left out for the patient. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LabOrderView(UUID id, String orderCode, LabOrder.Status status, LabOrder.Priority priority,
                               String clinicalNotes, UUID consultationId, Instant createdAt, OrderPatient patient,
                               OrderDoctor doctor, List<OrderItemView> items, BigDecimal total,
                               Instant cancelledAt, String cancellationReason) {
    }

    /** A row in an order list (lab queue, patient history). */
    public record LabOrderSummary(UUID id, String orderCode, LabOrder.Status status, LabOrder.Priority priority,
                                  Instant createdAt, UUID consultationId, UUID patientId, String patientCode,
                                  String patientName, String doctorName, int testCount, List<String> testNames,
                                  List<TubeType> tubes, BigDecimal total, boolean hasPrep) {
    }
}
