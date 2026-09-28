package com.cdlms.billing;

import com.cdlms.auth.AuthUser;
import com.cdlms.billing.BillingDtos.DiscountRequest;
import com.cdlms.billing.BillingDtos.InvoiceLine;
import com.cdlms.billing.BillingDtos.InvoicePatient;
import com.cdlms.billing.BillingDtos.InvoiceSummary;
import com.cdlms.billing.BillingDtos.InvoiceView;
import com.cdlms.billing.BillingDtos.PaymentRequest;
import com.cdlms.billing.BillingQueries.Filter;
import com.cdlms.billing.BillingQueries.UserRef;
import com.cdlms.common.ApiException;
import com.cdlms.common.ClinicTime;
import com.cdlms.common.PageResponse;
import com.cdlms.consultation.Consultation;
import com.cdlms.consultation.ConsultationRepository;
import com.cdlms.lab.LabOrder;
import com.cdlms.lab.LabOrderItem;
import com.cdlms.lab.LabOrderRepository;
import com.cdlms.patient.Patient;
import com.cdlms.patient.PatientRepository;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import com.cdlms.user.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Billing (Docs/Rules.md §3, ADR-023).
 *
 * <p>Invoices are created by the system, never typed in: finishing a consultation bills the visit
 * (doctor's fee plus the tests ordered in it), and a lab order placed outside a visit bills itself.
 * Removing an ordered test voids its line — unless the patient has already paid for it.
 *
 * <p>At the counter the receptionist takes payments (cash, card or UPI — part payments allowed, never
 * more than the balance) and may apply a discount up to the front-desk cap; an admin may discount
 * more. Every discount records who applied it and why, and every step is written to the invoice's
 * append-only history. Patients see their own invoices; clinicians and the lab see none.
 */
@Service
public class BillingService {

    static final int PATIENT_LIMIT = 50;

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final InvoiceEventRepository events;
    private final BillingQueries queries;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final ConsultationRepository consultations;
    private final LabOrderRepository labOrders;
    private final ClinicTime time;
    private final BigDecimal receptionDiscountCapPercent;

    public BillingService(InvoiceRepository invoices, PaymentRepository payments, InvoiceEventRepository events,
                          BillingQueries queries, PatientRepository patients, DoctorRepository doctors,
                          ConsultationRepository consultations, LabOrderRepository labOrders, ClinicTime time,
                          @Value("${app.billing.reception-discount-cap-percent:20}") BigDecimal receptionDiscountCapPercent) {
        this.invoices = invoices;
        this.payments = payments;
        this.events = events;
        this.queries = queries;
        this.patients = patients;
        this.doctors = doctors;
        this.consultations = consultations;
        this.labOrders = labOrders;
        this.time = time;
        this.receptionDiscountCapPercent = receptionDiscountCapPercent;
    }

    // ---------------------------------------------------------------- created by the system

    /** Bills a finished visit: the doctor's fee plus the tests on the consultation's open order. Idempotent. */
    @Transactional
    public Invoice invoiceVisit(Consultation consultation, Optional<LabOrder> order, UUID actorUserId) {
        Optional<Invoice> existing = invoices.findByConsultationId(consultation.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Doctor doctor = doctors.findById(consultation.getDoctorId()).orElseThrow();
        Invoice invoice = Invoice.forConsultation(invoices.nextCode(), consultation.getPatientId(), consultation.getId(),
                "Consultation — " + doctor.getFullName(), doctor.getConsultationFee());
        order.ifPresent(o -> o.liveItems().forEach(i -> invoice.addTest(i.getId(), i.getTestName(), i.getPriceAtOrder())));
        return created(invoice, actorUserId, "Visit");
    }

    /** Bills a lab order placed outside a visit. */
    @Transactional
    public Invoice invoiceLabOrder(LabOrder order, UUID actorUserId) {
        Invoice invoice = Invoice.forLabOrder(invoices.nextCode(), order.getPatientId(), order.getId());
        order.liveItems().forEach(i -> invoice.addTest(i.getId(), i.getTestName(), i.getPriceAtOrder()));
        return created(invoice, actorUserId, "Lab order " + order.getOrderCode());
    }

    private Invoice created(Invoice invoice, UUID actorUserId, String note) {
        Invoice saved = invoices.saveAndFlush(invoice);
        events.save(new InvoiceEvent(saved.getId(), InvoiceEvent.Type.CREATED, saved.net(), note, actorUserId));
        return saved;
    }

    /**
     * Takes cancelled tests off their invoices. Tests not billed yet (the visit isn't finished) are
     * skipped. A test the patient has already paid for can't be removed: {@code 409 ALREADY_PAID}.
     */
    @Transactional
    public void voidTests(List<LabOrderItem> items, UUID actorUserId, String note) {
        for (LabOrderItem item : items) {
            Optional<Invoice> billed = invoices.findByLabOrderItemId(item.getId());
            if (billed.isEmpty()) {
                continue;
            }
            Invoice invoice = billed.get();
            InvoiceItem line = invoice.lineFor(item.getId()).orElse(null);
            if (line == null) {
                continue;
            }
            if (!invoice.voidLine(line, time.now())) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PAID",
                        item.getTestName() + " has already been paid for on " + invoice.getInvoiceCode()
                                + ". The front desk needs to settle that first.");
            }
            invoices.saveAndFlush(invoice);
            events.save(new InvoiceEvent(invoice.getId(), InvoiceEvent.Type.LINE_VOIDED, line.getAmount(),
                    note + ": " + item.getTestName(), actorUserId));
            if (invoice.getStatus() == Invoice.Status.VOID) {
                events.save(new InvoiceEvent(invoice.getId(), InvoiceEvent.Type.VOIDED, null,
                        "Nothing left to bill", actorUserId));
            }
        }
    }

    // ---------------------------------------------------------------- counter (receptionist, admin)

    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> list(Filter filter, String query, int page, int size) {
        return new PageResponse<>(queries.list(filter, query, size, page * size), page, size, queries.count(filter, query));
    }

    @Transactional
    public InvoiceView pay(AuthUser receptionist, UUID id, PaymentRequest request) {
        Invoice invoice = openInvoice(id);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        if (amount.compareTo(invoice.balance()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_TOO_HIGH",
                    "That's more than the balance of ₹" + invoice.balance().toPlainString());
        }
        payments.save(new Payment(invoice.getId(), amount, request.method(), trim(request.reference()),
                receptionist.id(), time.now()));
        invoice.recordPayment(amount, time.now());
        invoices.saveAndFlush(invoice);
        events.save(new InvoiceEvent(invoice.getId(), InvoiceEvent.Type.PAYMENT_RECORDED, amount,
                request.method().name(), receptionist.id()));
        return view(invoice);
    }

    /**
     * Sets the discount (0 removes it). Needs a reason; the front desk may give up to the configured
     * share of the bill, an admin more. Who applied it is always recorded.
     */
    @Transactional
    public InvoiceView discount(AuthUser staff, UUID id, DiscountRequest request) {
        Invoice invoice = openInvoice(id);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        String reason = trim(request.reason());
        if (amount.signum() > 0 && reason == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Say why the discount is given");
        }
        if (staff.role() == Role.RECEPTIONIST && amount.compareTo(receptionCap(invoice)) > 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DISCOUNT_OVER_LIMIT",
                    "The front desk can discount up to " + receptionDiscountCapPercent.stripTrailingZeros().toPlainString()
                            + "% (₹" + receptionCap(invoice).toPlainString() + "). Ask an admin for more.");
        }
        switch (invoice.checkDiscount(amount)) {
            case MORE_THAN_BILL -> throw new ApiException(HttpStatus.BAD_REQUEST, "DISCOUNT_TOO_HIGH",
                    "The discount can't be more than the bill");
            case BELOW_PAID -> throw new ApiException(HttpStatus.BAD_REQUEST, "DISCOUNT_TOO_HIGH",
                    "The patient has already paid more than the bill would be after this discount");
            case NONE -> {
            }
        }
        invoice.applyDiscount(amount, reason, staff.id(), time.now());
        invoices.saveAndFlush(invoice);
        events.save(new InvoiceEvent(invoice.getId(), InvoiceEvent.Type.DISCOUNT_APPLIED, amount,
                reason == null ? "Discount removed" : reason, staff.id()));
        return view(invoice);
    }

    private BigDecimal receptionCap(Invoice invoice) {
        return invoice.gross().multiply(receptionDiscountCapPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
    }

    // ---------------------------------------------------------------- reads

    /** One invoice: patients only their own; the front desk and admins any. */
    @Transactional(readOnly = true)
    public InvoiceView get(AuthUser caller, UUID id) {
        Invoice invoice = invoices.findById(id).orElseThrow(() -> notFound("Invoice not found"));
        if (caller.role() == Role.PATIENT && !ownPatientId(caller).equals(invoice.getPatientId())) {
            throw notFound("Invoice not found");
        }
        if (caller.role() == Role.PATIENT && invoice.getStatus() == Invoice.Status.VOID) {
            throw notFound("Invoice not found");
        }
        return view(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceSummary> mine(AuthUser patient) {
        return queries.forPatient(ownPatientId(patient), PATIENT_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<InvoiceSummary> forPatient(UUID patientId) {
        if (!patients.existsById(patientId)) {
            throw notFound("Patient not found");
        }
        return queries.forPatient(patientId, PATIENT_LIMIT);
    }

    // ---------------------------------------------------------------- helpers

    private Invoice openInvoice(UUID id) {
        Invoice invoice = invoices.findById(id).orElseThrow(() -> notFound("Invoice not found"));
        if (!invoice.isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, "INVOICE_CLOSED", invoice.getStatus() == Invoice.Status.PAID
                    ? "This invoice is already paid in full"
                    : "This invoice has been voided");
        }
        return invoice;
    }

    InvoiceView view(Invoice invoice) {
        Patient p = patients.findById(invoice.getPatientId()).orElseThrow();
        String doctorName = null;
        String labOrderCode = null;
        if (invoice.getConsultationId() != null) {
            doctorName = consultations.findById(invoice.getConsultationId())
                    .flatMap(c -> doctors.findById(c.getDoctorId())).map(Doctor::getFullName).orElse(null);
        } else {
            LabOrder order = labOrders.findById(invoice.getLabOrderId()).orElseThrow();
            labOrderCode = order.getOrderCode();
            doctorName = doctors.findById(order.getOrderingDoctorId()).map(Doctor::getFullName).orElse(null);
        }
        BillingDtos.Discount discount = null;
        if (invoice.getDiscount().signum() > 0) {
            UserRef by = queries.user(invoice.getDiscountByUserId());
            discount = new BillingDtos.Discount(invoice.getDiscount(), invoice.getDiscountReason(), by.name(), by.role(),
                    invoice.getDiscountAt());
        }
        return new InvoiceView(invoice.getId(), invoice.getInvoiceCode(), invoice.getStatus(), invoice.getCreatedAt(),
                invoice.getPaidAt(), new InvoicePatient(p.getId(), p.getPatientCode(), p.getFullName(), p.getPhone()),
                doctorName, invoice.getConsultationId(), invoice.getLabOrderId(), labOrderCode,
                invoice.getItems().stream().map(l -> new InvoiceLine(l.getId(), l.getKind(), l.getDescription(),
                        l.getAmount(), l.getVoidedAt())).toList(),
                invoice.getConsultationFee(), invoice.getTestChargesTotal(), invoice.gross(), discount, invoice.net(),
                invoice.getAmountPaid(), invoice.balance(), queries.payments(invoice.getId()),
                receptionDiscountCapPercent);
    }

    private UUID ownPatientId(AuthUser patient) {
        return patients.findByUserId(patient.id()).map(Patient::getId)
                .orElseThrow(() -> notFound("No patient record for this account"));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
