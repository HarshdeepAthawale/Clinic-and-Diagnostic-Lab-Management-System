package com.cdlms.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for invoices, payments and discounts (Docs/API.md "Billing"). */
public final class BillingDtos {

    private BillingDtos() {
    }

    // ---------------------------------------------------------------- requests

    public record PaymentRequest(
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2) BigDecimal amount,
            @NotNull Payment.Method method,
            @Size(max = 60) String reference) {
    }

    /** {@code amount} 0 removes the discount; any other amount needs a reason. */
    public record DiscountRequest(
            @NotNull @DecimalMin("0.00") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2) BigDecimal amount,
            @Size(max = 300) String reason) {
    }

    // ---------------------------------------------------------------- responses

    public record InvoicePatient(UUID id, String patientCode, String fullName, String phone) {
    }

    public record InvoiceLine(UUID id, InvoiceItem.Kind kind, String description, BigDecimal amount, Instant voidedAt) {
    }

    public record PaymentView(UUID id, BigDecimal amount, Payment.Method method, String reference,
                              String receivedBy, Instant receivedAt) {
    }

    public record Discount(BigDecimal amount, String reason, String appliedBy, String appliedByRole, Instant appliedAt) {
    }

    /** One invoice in full. {@code discount} is present only when a discount is applied. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InvoiceView(UUID id, String invoiceCode, Invoice.Status status, Instant createdAt, Instant paidAt,
                              InvoicePatient patient, String doctorName, UUID consultationId, UUID labOrderId,
                              String labOrderCode, List<InvoiceLine> lines, BigDecimal consultationFee,
                              BigDecimal testChargesTotal, BigDecimal gross, Discount discount, BigDecimal net,
                              BigDecimal amountPaid, BigDecimal balance, List<PaymentView> payments,
                              BigDecimal discountCapPercent) {
    }

    /** A row in an invoice list (counter, patient history). */
    public record InvoiceSummary(UUID id, String invoiceCode, Invoice.Status status, Instant createdAt,
                                 UUID patientId, String patientCode, String patientName, String doctorName,
                                 boolean labOnly, int lineCount, BigDecimal net, BigDecimal amountPaid,
                                 BigDecimal balance) {
    }

    /** Today's takings at the counter, per payment method. */
    public record Collections(BigDecimal total, BigDecimal cash, BigDecimal card, BigDecimal upi, long payments) {
    }
}
