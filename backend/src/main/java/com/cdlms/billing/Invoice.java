package com.cdlms.billing;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A bill (Docs/Schema.md §4, ADR-023), numbered like {@code INV-000123}. A visit invoice bills a
 * consultation: the doctor's fee plus the tests ordered in it. A lab-only invoice bills a lab order
 * placed outside a visit. Totals are kept in step with the live lines; the status follows the
 * payments: unpaid, partly paid, paid — or void when nothing is left to bill.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    public enum Status { UNPAID, PARTIALLY_PAID, PAID, VOID }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_code", nullable = false, unique = true, updatable = false)
    private String invoiceCode;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "consultation_id", unique = true, updatable = false)
    private UUID consultationId;

    @Column(name = "lab_order_id", unique = true, updatable = false)
    private UUID labOrderId;

    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal consultationFee = BigDecimal.ZERO;

    @Column(name = "test_charges_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal testChargesTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "discount_reason")
    private String discountReason;

    @Column(name = "discount_by_user_id")
    private UUID discountByUserId;

    @Column(name = "discount_at")
    private Instant discountAt;

    @Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.UNPAID;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    @OrderBy("createdAt")
    private List<InvoiceItem> items = new ArrayList<>();

    protected Invoice() {
    }

    private Invoice(String invoiceCode, UUID patientId, UUID consultationId, UUID labOrderId) {
        this.invoiceCode = invoiceCode;
        this.patientId = patientId;
        this.consultationId = consultationId;
        this.labOrderId = labOrderId;
    }

    /** A visit invoice, starting with the consultation fee. */
    public static Invoice forConsultation(String code, UUID patientId, UUID consultationId, String feeDescription,
                                         BigDecimal fee) {
        Invoice invoice = new Invoice(code, patientId, consultationId, null);
        invoice.items.add(new InvoiceItem(invoice, InvoiceItem.Kind.CONSULTATION, feeDescription, fee, null));
        invoice.recompute(null);
        return invoice;
    }

    /** A lab-only invoice for a lab order placed outside a visit. */
    public static Invoice forLabOrder(String code, UUID patientId, UUID labOrderId) {
        return new Invoice(code, patientId, null, labOrderId);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ---------------------------------------------------------------- lines

    public void addTest(UUID labOrderItemId, String testName, BigDecimal price) {
        items.add(new InvoiceItem(this, InvoiceItem.Kind.LAB_TEST, testName, price, labOrderItemId));
        recompute(null);
    }

    /** The live line billing this ordered test, if any. */
    public Optional<InvoiceItem> lineFor(UUID labOrderItemId) {
        return items.stream().filter(i -> i.isLive() && labOrderItemId.equals(i.getLabOrderItemId())).findFirst();
    }

    /**
     * Voids a line. Refused (returns false, nothing changes) when the patient has already paid more
     * than the invoice would then be worth — that needs a refund at the counter first.
     */
    public boolean voidLine(InvoiceItem line, Instant at) {
        BigDecimal newGross = gross().subtract(line.getAmount());
        BigDecimal newDiscount = discount.min(newGross);
        if (amountPaid.compareTo(newGross.subtract(newDiscount)) > 0) {
            return false;
        }
        line.voidAt(at);
        // A discount can never exceed what's left to bill.
        discount = newDiscount;
        recompute(at);
        return true;
    }

    // ---------------------------------------------------------------- money

    public enum DiscountProblem { NONE, MORE_THAN_BILL, BELOW_PAID }

    /** Checks a discount before applying it: not more than the bill, and not below what's been paid. */
    public DiscountProblem checkDiscount(BigDecimal amount) {
        if (amount.compareTo(gross()) > 0) {
            return DiscountProblem.MORE_THAN_BILL;
        }
        if (gross().subtract(amount).compareTo(amountPaid) < 0) {
            return DiscountProblem.BELOW_PAID;
        }
        return DiscountProblem.NONE;
    }

    /** Sets (or changes) the discount, recording who applied it, why and when. */
    public void applyDiscount(BigDecimal amount, String reason, UUID byUserId, Instant at) {
        if (checkDiscount(amount) != DiscountProblem.NONE) {
            throw new IllegalArgumentException("Invalid discount " + amount + " on " + invoiceCode);
        }
        discount = amount;
        if (amount.signum() == 0) {
            discountReason = null;
            discountByUserId = null;
            discountAt = null;
        } else {
            discountReason = reason;
            discountByUserId = byUserId;
            discountAt = at;
        }
        recompute(at);
    }

    public void recordPayment(BigDecimal amount, Instant at) {
        if (amount.signum() <= 0 || amount.compareTo(balance()) > 0) {
            throw new IllegalArgumentException("Payment " + amount + " exceeds the balance of " + invoiceCode);
        }
        amountPaid = amountPaid.add(amount);
        recompute(at);
    }

    private void recompute(Instant at) {
        consultationFee = sum(InvoiceItem.Kind.CONSULTATION);
        testChargesTotal = sum(InvoiceItem.Kind.LAB_TEST);
        if (items.stream().noneMatch(InvoiceItem::isLive)) {
            status = Status.VOID;
            paidAt = null;
        } else if (balance().signum() == 0) {
            if (status != Status.PAID) {
                paidAt = at != null ? at : Instant.now();
            }
            status = Status.PAID;
        } else {
            status = amountPaid.signum() == 0 ? Status.UNPAID : Status.PARTIALLY_PAID;
            paidAt = null;
        }
    }

    private BigDecimal sum(InvoiceItem.Kind kind) {
        return items.stream().filter(i -> i.isLive() && i.getKind() == kind)
                .map(InvoiceItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal gross() {
        return consultationFee.add(testChargesTotal);
    }

    public BigDecimal net() {
        return gross().subtract(discount);
    }

    public BigDecimal balance() {
        return net().subtract(amountPaid);
    }

    public boolean isOpen() {
        return status == Status.UNPAID || status == Status.PARTIALLY_PAID;
    }

    // ---------------------------------------------------------------- getters

    public UUID getId() {
        return id;
    }

    public String getInvoiceCode() {
        return invoiceCode;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getConsultationId() {
        return consultationId;
    }

    public UUID getLabOrderId() {
        return labOrderId;
    }

    public BigDecimal getConsultationFee() {
        return consultationFee;
    }

    public BigDecimal getTestChargesTotal() {
        return testChargesTotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public String getDiscountReason() {
        return discountReason;
    }

    public UUID getDiscountByUserId() {
        return discountByUserId;
    }

    public Instant getDiscountAt() {
        return discountAt;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }
}
