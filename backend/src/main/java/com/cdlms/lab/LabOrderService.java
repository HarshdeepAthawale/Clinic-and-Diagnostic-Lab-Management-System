package com.cdlms.lab;

import com.cdlms.auth.AuthUser;
import com.cdlms.billing.BillingService;
import com.cdlms.common.ApiException;
import com.cdlms.common.ClinicTime;
import com.cdlms.common.PageResponse;
import com.cdlms.consultation.Consultation;
import com.cdlms.consultation.ConsultationRepository;
import com.cdlms.lab.LabDtos.LabOrderSummary;
import com.cdlms.lab.LabDtos.LabOrderView;
import com.cdlms.lab.LabDtos.OrderDoctor;
import com.cdlms.lab.LabDtos.OrderItemView;
import com.cdlms.lab.LabDtos.OrderPatient;
import com.cdlms.lab.LabDtos.OrderRequest;
import com.cdlms.patient.Patient;
import com.cdlms.patient.PatientAccessLog;
import com.cdlms.patient.PatientAccessLog.Resource;
import com.cdlms.patient.PatientAccessLogRepository;
import com.cdlms.patient.PatientRepository;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lab orders (Docs/Rules.md §2a, ADR-022).
 *
 * <p>Ordering: a doctor orders tests either inside their own open consultation — the patient comes
 * from the consultation and repeated orders add to the same open order — or directly for a patient
 * they have a care relationship with. Only active catalog tests can be ordered; each line copies the
 * test's name and price. The order exists the moment the request returns and lands in the lab's queue.
 *
 * <p>Access: the patient sees their own orders (with prep instructions, without the doctor's
 * clinical notes); doctors see orders they placed or for patients under their care (logged); lab
 * technicians and pathologists see all orders, since they process them.
 */
@Service
public class LabOrderService {

    static final int PATIENT_LIMIT = 30;

    private final LabOrderRepository orders;
    private final LabTestRepository tests;
    private final LabQueries queries;
    private final ConsultationRepository consultations;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final PatientAccessLogRepository accessLog;
    private final BillingService billing;
    private final ClinicTime time;

    public LabOrderService(LabOrderRepository orders, LabTestRepository tests, LabQueries queries,
                           ConsultationRepository consultations, PatientRepository patients, DoctorRepository doctors,
                           PatientAccessLogRepository accessLog, BillingService billing, ClinicTime time) {
        this.orders = orders;
        this.tests = tests;
        this.queries = queries;
        this.consultations = consultations;
        this.patients = patients;
        this.doctors = doctors;
        this.accessLog = accessLog;
        this.billing = billing;
        this.time = time;
    }

    // ---------------------------------------------------------------- ordering (doctor)

    @Transactional
    public LabOrderView order(AuthUser doctor, OrderRequest request) {
        Doctor me = doctorOf(doctor);
        List<LabTest> ordered = activeTests(request.testIds());
        LabOrder.Priority priority = request.priority() == null ? LabOrder.Priority.ROUTINE : request.priority();
        String notes = trim(request.clinicalNotes());

        LabOrder order;
        if (request.consultationId() != null) {
            Consultation consultation = consultations.findById(request.consultationId())
                    .filter(c -> c.getDoctorId().equals(me.getId()))
                    .orElseThrow(() -> notFound("Consultation not found"));
            if (!consultation.isDraft()) {
                throw conflict("CONSULTATION_LOCKED",
                        "This consultation is finished. Order tests from the patient's record instead.");
            }
            if (request.patientId() != null && !request.patientId().equals(consultation.getPatientId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "The patient doesn't match the consultation");
            }
            order = orders.findByConsultationIdAndStatus(consultation.getId(), LabOrder.Status.ORDERED)
                    .orElseGet(() -> new LabOrder(orders.nextCode(), consultation.getPatientId(), me.getId(),
                            consultation.getId()));
            // Adding tests later in the visit may raise the priority or add notes, never silently clear them.
            order.setDetails(order.getPriority() == LabOrder.Priority.URGENT ? LabOrder.Priority.URGENT : priority,
                    notes != null ? notes : order.getClinicalNotes());
        } else {
            if (request.patientId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Choose the patient, or order from a consultation");
            }
            if (!patients.existsById(request.patientId())) {
                throw notFound("Patient not found");
            }
            if (!hasCareRelationship(me, request.patientId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "NO_CARE_RELATIONSHIP",
                        "You don't have an appointment with this patient");
            }
            order = new LabOrder(orders.nextCode(), request.patientId(), me.getId(), null);
            order.setDetails(priority, notes);
        }

        boolean added = false;
        for (LabTest test : ordered) {
            added |= order.addTest(test);
        }
        if (!added && order.getId() != null) {
            throw conflict("ALREADY_ORDERED", ordered.size() == 1
                    ? ordered.getFirst().getName() + " is already on this order"
                    : "These tests are already on this order");
        }
        LabOrder saved = orders.saveAndFlush(order);
        // Outside a visit the order is billed at once; in a visit, finishing the consultation bills it.
        if (saved.getConsultationId() == null) {
            billing.invoiceLabOrder(saved, doctor.id());
        }
        return view(saved, true);
    }

    /** Removes one test from an open order; the last one removed cancels the order. */
    @Transactional
    public LabOrderView removeItem(AuthUser doctor, UUID orderId, UUID itemId) {
        LabOrder order = ownOpenOrder(doctor, orderId);
        LabOrderItem item = order.liveItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> notFound("That test isn't on this order"));
        billing.voidTests(List.of(item), doctor.id(), "Removed from order " + order.getOrderCode());
        order.cancelItem(item, doctor.id(), time.now());
        return view(orders.saveAndFlush(order), true);
    }

    @Transactional
    public LabOrderView cancel(AuthUser doctor, UUID orderId, String reason) {
        LabOrder order = ownOpenOrder(doctor, orderId);
        billing.voidTests(order.liveItems(), doctor.id(), "Order " + order.getOrderCode() + " cancelled");
        order.cancel(doctor.id(), time.now(), trim(reason) == null ? "Cancelled by the doctor" : trim(reason));
        return view(orders.saveAndFlush(order), true);
    }

    // ---------------------------------------------------------------- reads

    @Transactional
    public LabOrderView get(AuthUser caller, UUID id) {
        LabOrder order = orders.findById(id).orElseThrow(() -> notFound("Order not found"));
        switch (caller.role()) {
            case PATIENT -> {
                if (!ownPatientId(caller).equals(order.getPatientId())) {
                    throw notFound("Order not found");
                }
                return view(order, false);
            }
            case DOCTOR -> {
                Doctor me = doctorOf(caller);
                if (!order.getOrderingDoctorId().equals(me.getId()) && !hasCareRelationship(me, order.getPatientId())) {
                    throw notFound("Order not found");
                }
                accessLog.save(new PatientAccessLog(order.getPatientId(), caller.id(), Resource.LAB_HISTORY, order.getId()));
                return view(order, true);
            }
            case LAB_TECHNICIAN, PATHOLOGIST -> {
                return view(order, true);
            }
            default -> throw notFound("Order not found");
        }
    }

    /** The open order the doctor has placed in this consultation, if any (for the consult workspace). */
    @Transactional(readOnly = true)
    public Optional<LabOrderView> forConsultation(AuthUser doctor, UUID consultationId) {
        Doctor me = doctorOf(doctor);
        Consultation consultation = consultations.findById(consultationId)
                .filter(c -> c.getDoctorId().equals(me.getId()))
                .orElseThrow(() -> notFound("Consultation not found"));
        return orders.findByConsultationIdAndStatus(consultation.getId(), LabOrder.Status.ORDERED)
                .map(o -> view(o, true));
    }

    /** The patient's own orders with every test's prep instructions, newest first. */
    @Transactional(readOnly = true)
    public List<LabOrderView> mine(AuthUser patient) {
        return orders.findTop30ByPatientIdOrderByCreatedAtDesc(ownPatientId(patient)).stream()
                .map(o -> view(o, false))
                .toList();
    }

    /** A patient's open orders with prep, for their dashboard (the caller has already resolved the patient). */
    @Transactional(readOnly = true)
    public List<LabOrderView> openForPatient(UUID patientId) {
        return orders.findTop5ByPatientIdAndStatusOrderByCreatedAtDesc(patientId, LabOrder.Status.ORDERED).stream()
                .map(o -> view(o, false))
                .toList();
    }

    /** A patient's orders as list rows: the patient themself, or a doctor with a care relationship (logged). */
    @Transactional
    public List<LabOrderSummary> forPatient(AuthUser caller, UUID patientId) {
        switch (caller.role()) {
            case PATIENT -> {
                if (!ownPatientId(caller).equals(patientId)) {
                    throw forbidden();
                }
            }
            case DOCTOR -> {
                Doctor me = doctorOf(caller);
                if (!patients.existsById(patientId)) {
                    throw notFound("Patient not found");
                }
                if (!hasCareRelationship(me, patientId)) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "NO_CARE_RELATIONSHIP",
                            "You don't have an appointment with this patient");
                }
                accessLog.save(new PatientAccessLog(patientId, caller.id(), Resource.LAB_HISTORY, null));
            }
            default -> throw forbidden();
        }
        return queries.forPatient(patientId, PATIENT_LIMIT);
    }

    /** The lab's incoming queue: open orders, urgent first, oldest first. */
    @Transactional(readOnly = true)
    public PageResponse<LabOrderSummary> queue(int page, int size) {
        return new PageResponse<>(queries.openOrders(size, page * size), page, size, queries.countOpenOrders());
    }

    // ---------------------------------------------------------------- helpers

    private List<LabTest> activeTests(List<UUID> ids) {
        List<UUID> unique = List.copyOf(new LinkedHashSet<>(ids));
        Map<UUID, LabTest> found = tests.findByIdIn(unique).stream()
                .collect(Collectors.toMap(LabTest::getId, Function.identity()));
        List<LabTest> result = unique.stream().map(found::get).toList();
        if (result.stream().anyMatch(t -> t == null || !t.isActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_TEST",
                    "One of the tests isn't in the catalog any more. Refresh and try again.");
        }
        return result;
    }

    private LabOrder ownOpenOrder(AuthUser doctor, UUID orderId) {
        LabOrder order = orders.findById(orderId)
                .filter(o -> o.getOrderingDoctorId().equals(doctorOf(doctor).getId()))
                .orElseThrow(() -> notFound("Order not found"));
        if (!order.isOpen()) {
            throw conflict("ORDER_CANCELLED", "This order has already been cancelled");
        }
        return order;
    }

    private LabOrderView view(LabOrder order, boolean includeNotes) {
        Patient p = patients.findById(order.getPatientId()).orElseThrow();
        Doctor d = doctors.findById(order.getOrderingDoctorId()).orElseThrow();
        // A cancelled order still shows what was on it; an open one shows only the live tests.
        List<LabOrderItem> items = order.isOpen() ? order.liveItems() : order.getItems();
        Map<UUID, LabTest> catalog = tests.findByIdIn(items.stream().map(LabOrderItem::getLabTestId).toList())
                .stream().collect(Collectors.toMap(LabTest::getId, Function.identity()));
        List<OrderItemView> lines = items.stream().map(i -> {
            LabTest t = catalog.get(i.getLabTestId());
            return new OrderItemView(i.getId(), t.getId(), t.getCode(), i.getTestName(), t.getCategory(),
                    t.getSampleType(), t.getRequiredTubeType(), i.getPriceAtOrder(), t.getTurnaroundHours(),
                    t.getPrepInstructions(), i.getStatus());
        }).toList();
        BigDecimal total = lines.stream().map(OrderItemView::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LabOrderView(order.getId(), order.getOrderCode(), order.getStatus(), order.getPriority(),
                includeNotes ? order.getClinicalNotes() : null, order.getConsultationId(), order.getCreatedAt(),
                new OrderPatient(p.getId(), p.getPatientCode(), p.getFullName(), p.ageOn(time.today()),
                        p.getGender().name()),
                new OrderDoctor(d.getId(), d.getFullName(), d.getSpecialization()),
                lines, total, order.getCancelledAt(), order.getCancellationReason());
    }

    private boolean hasCareRelationship(Doctor doctor, UUID patientId) {
        return patients.findWithCareRelationship(patientId, doctor.getId()).isPresent();
    }

    private Doctor doctorOf(AuthUser doctor) {
        return doctors.findByUserId(doctor.id())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No doctor profile for this account"));
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

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource");
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
