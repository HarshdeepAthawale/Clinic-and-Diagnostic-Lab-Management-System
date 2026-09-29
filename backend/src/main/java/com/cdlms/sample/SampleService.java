package com.cdlms.sample;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.common.ClinicTime;
import com.cdlms.common.PageResponse;
import com.cdlms.lab.LabOrder;
import com.cdlms.lab.LabOrderItem;
import com.cdlms.lab.LabOrderRepository;
import com.cdlms.lab.LabTest;
import com.cdlms.lab.LabTestRepository;
import com.cdlms.lab.TubeType;
import com.cdlms.patient.Patient;
import com.cdlms.patient.PatientAccessLog;
import com.cdlms.patient.PatientAccessLog.Resource;
import com.cdlms.patient.PatientAccessLogRepository;
import com.cdlms.patient.PatientRepository;
import com.cdlms.sample.RejectionRecord.Reason;
import com.cdlms.sample.RejectionRecord.Stage;
import com.cdlms.sample.SampleDtos.CollectRequest;
import com.cdlms.sample.SampleDtos.ReceiveRequest;
import com.cdlms.sample.SampleDtos.Rejection;
import com.cdlms.sample.SampleDtos.SampleEvent;
import com.cdlms.sample.SampleDtos.SamplePatient;
import com.cdlms.sample.SampleDtos.SampleSummary;
import com.cdlms.sample.SampleDtos.SampleTest;
import com.cdlms.sample.SampleDtos.SampleView;
import com.cdlms.sample.SampleQueries.UserRef;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import com.cdlms.user.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Samples, from order to the receipt check (Docs/Rules.md §2, ADR-024).
 *
 * <p>Samples are created by the system when tests are ordered — one per physical tube, shared by all
 * of the order's tests that need it. The lab technician collects each (recording the tube and body
 * site; a wrong tube needs an explicit confirmation), then either accepts it at the receipt check or
 * rejects it. A rejection is permanent, tells the front desk to call the patient back, and creates
 * the redraw sample straight away. Every step is written to the append-only chain of custody.
 *
 * <p>Once a sample has been collected its tests can no longer be taken off the order.
 */
@Service
public class SampleService {

    static final int LIST_LIMIT = 50;

    private final SampleRepository samples;
    private final SampleStatusEventRepository events;
    private final RejectionRecordRepository rejections;
    private final NotificationRepository notifications;
    private final SampleCodes codes;
    private final SampleQueries queries;
    private final LabOrderRepository orders;
    private final LabTestRepository tests;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final PatientAccessLogRepository accessLog;
    private final ClinicTime time;

    public SampleService(SampleRepository samples, SampleStatusEventRepository events,
                         RejectionRecordRepository rejections, NotificationRepository notifications, SampleCodes codes,
                         SampleQueries queries, LabOrderRepository orders, LabTestRepository tests,
                         PatientRepository patients, DoctorRepository doctors, PatientAccessLogRepository accessLog,
                         ClinicTime time) {
        this.samples = samples;
        this.events = events;
        this.rejections = rejections;
        this.notifications = notifications;
        this.codes = codes;
        this.queries = queries;
        this.orders = orders;
        this.tests = tests;
        this.patients = patients;
        this.doctors = doctors;
        this.accessLog = accessLog;
        this.time = time;
    }

    // ---------------------------------------------------------------- created by the system

    /**
     * Makes sure every given order line has a sample: it joins the order's not-yet-collected sample for
     * the same tube, or gets a new one. Called when tests are ordered.
     */
    @Transactional
    public void createFor(LabOrder order, Collection<LabOrderItem> items, UUID actorUserId) {
        Map<UUID, LabTest> catalog = tests.findByIdIn(items.stream().map(LabOrderItem::getLabTestId).toList()).stream()
                .collect(Collectors.toMap(LabTest::getId, Function.identity()));
        Map<TubeType, List<LabOrderItem>> byTube = new EnumMap<>(TubeType.class);
        for (LabOrderItem item : items) {
            byTube.computeIfAbsent(catalog.get(item.getLabTestId()).getRequiredTubeType(), t -> new ArrayList<>()).add(item);
        }
        byTube.forEach((tube, lines) -> {
            Sample sample = samples.findFirstByLabOrderIdAndRequiredTubeTypeAndStatus(order.getId(), tube, SampleStatus.ORDERED)
                    .orElseGet(() -> {
                        Sample created = samples.saveAndFlush(
                                new Sample(codes.next(), order.getId(), order.getPatientId(), tube, null));
                        record(created, SampleStatus.ORDERED, actorUserId, "Ordered with " + order.getOrderCode());
                        return created;
                    });
            lines.forEach(l -> sample.getItemIds().add(l.getId()));
            samples.saveAndFlush(sample);
        });
    }

    /**
     * Refuses to take tests off an order once their sample has been drawn: {@code 409 SAMPLE_COLLECTED}.
     * Tests whose sample is still waiting (or that have no sample yet) can go.
     */
    @Transactional(readOnly = true)
    public void requireRemovable(Collection<LabOrderItem> items) {
        for (LabOrderItem item : items) {
            samples.findLiveByItemId(item.getId()).ifPresent(sample -> {
                if (sample.getStatus() != SampleStatus.ORDERED) {
                    throw new ApiException(HttpStatus.CONFLICT, "SAMPLE_COLLECTED",
                            item.getTestName() + " can't be removed — its sample " + sample.getSampleCode()
                                    + " has already been collected.");
                }
            });
        }
    }

    /** Detaches removed tests from their waiting samples; a sample left with no tests is cancelled. */
    @Transactional
    public void release(Collection<LabOrderItem> items, UUID actorUserId, String why) {
        Collection<UUID> ids = items.stream().map(LabOrderItem::getId).toList();
        if (ids.isEmpty()) {
            return;
        }
        for (Sample sample : samples.findLiveByItemIds(ids)) {
            if (sample.getStatus() != SampleStatus.ORDERED) {
                continue;
            }
            sample.getItemIds().removeAll(ids);
            if (sample.getItemIds().isEmpty()) {
                sample.cancel();
                record(sample, SampleStatus.CANCELLED, actorUserId, why);
            }
            samples.saveAndFlush(sample);
        }
    }

    // ---------------------------------------------------------------- lab technician workflow

    @Transactional
    public SampleView collect(AuthUser tech, UUID id, CollectRequest request) {
        Sample sample = find(id);
        if (sample.getStatus() != SampleStatus.ORDERED) {
            throw conflict("NOT_WAITING_FOR_COLLECTION", "This sample is already " + describe(sample.getStatus()));
        }
        String site = trim(request.bodySite());
        if (request.tubeTypeUsed().isBlood() && site == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BODY_SITE_REQUIRED", "Say where the blood was drawn from");
        }
        boolean mismatch = request.tubeTypeUsed() != sample.getRequiredTubeType();
        if (mismatch && !request.confirmMismatch()) {
            throw conflict("TUBE_MISMATCH", "These tests need the " + label(sample.getRequiredTubeType()) + " tube, not "
                    + label(request.tubeTypeUsed()) + ". Use the right tube, or confirm to record the mismatch.");
        }
        sample.collect(request.tubeTypeUsed(), mismatch, site, tech.id(), time.now());
        samples.saveAndFlush(sample);
        record(sample, SampleStatus.COLLECTED, tech.id(), (site == null ? "" : site + " · ") + label(request.tubeTypeUsed())
                + (mismatch ? " — TUBE MISMATCH (needs " + label(sample.getRequiredTubeType()) + ")" : ""));
        return view(sample, tech.role());
    }

    /** The receipt check: accept the sample, or reject it (which notifies the front desk and creates the redraw). */
    @Transactional
    public SampleView receive(AuthUser tech, UUID id, ReceiveRequest request) {
        Sample sample = find(id);
        if (sample.getStatus() != SampleStatus.COLLECTED) {
            throw conflict("NOT_WAITING_FOR_RECEIPT", "This sample is " + describe(sample.getStatus())
                    + ", so it can't go through the receipt check");
        }
        if (request.accepted()) {
            sample.receive(tech.id(), time.now());
            samples.saveAndFlush(sample);
            record(sample, SampleStatus.RECEIVED_AT_LAB, tech.id(),
                    sample.isTubeMismatch() ? "Accepted with a tube mismatch" : "Passed the quality check");
        } else {
            reject(sample, Stage.RECEIVED_AT_LAB, request.reason(), request.note(), tech.id());
        }
        return view(sample, tech.role());
    }

    /**
     * Rejects a sample at either stage: keeps it as a permanent record, tells the front desk to call the
     * patient back, and creates the redraw. Phase 08 reuses this for rejection during testing.
     */
    @Transactional
    public Sample reject(Sample sample, Stage stage, Reason reason, String note, UUID actorUserId) {
        if (reason == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Choose why the sample is being rejected");
        }
        if (!reason.allowedAt(stage)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_NOT_ALLOWED", "That reason doesn't apply at this stage");
        }
        String cleanNote = trim(note);
        if (reason == Reason.OTHER && cleanNote == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTE_REQUIRED", "Add a note explaining the rejection");
        }

        sample.reject();
        samples.saveAndFlush(sample);
        rejections.save(new RejectionRecord(sample.getId(), stage, reason, cleanNote, actorUserId, time.now()));
        record(sample, SampleStatus.REJECTED, actorUserId, describe(reason) + (cleanNote == null ? "" : " — " + cleanNote));

        Patient patient = patients.findById(sample.getPatientId()).orElseThrow();
        notifications.save(new Notification(Role.RECEPTIONIST, Notification.Type.SAMPLE_REJECTED,
                "Redraw needed — " + patient.getFullName(),
                "Sample " + sample.getSampleCode() + " (" + label(sample.getRequiredTubeType()) + ") was rejected: "
                        + describe(reason) + ". Call " + patient.getFullName() + " ("
                        + (patient.getPhone() == null ? "no phone on file" : patient.getPhone()) + ") to come in for a new sample.",
                patient.getId(), sample.getId()));

        Sample redraw = new Sample(codes.next(), sample.getLabOrderId(), sample.getPatientId(),
                sample.getRequiredTubeType(), sample.getId());
        redraw.getItemIds().addAll(sample.getItemIds());
        redraw = samples.saveAndFlush(redraw);
        record(redraw, SampleStatus.ORDERED, actorUserId, "Redraw of " + sample.getSampleCode());
        return redraw;
    }

    // ---------------------------------------------------------------- reads

    /** One sample: lab staff see everything; others only what their role needs (see {@link #view}). */
    @Transactional
    public SampleView get(AuthUser caller, UUID id) {
        Sample sample = find(id);
        checkCanSee(caller, sample);
        return view(sample, caller.role());
    }

    @Transactional(readOnly = true)
    public SampleView byCode(AuthUser caller, String code) {
        Sample sample = samples.findBySampleCodeIgnoreCase(code.trim())
                .orElseThrow(() -> notFound("No sample with that code"));
        return view(sample, caller.role());
    }

    /** The full chain-of-custody log, with who did each step. */
    @Transactional(readOnly = true)
    public List<SampleEvent> chainOfCustody(UUID id) {
        find(id);
        return eventViews(id, true);
    }

    /** The samples of one order, with their journeys — for the order page and the patient's Lab tests page. */
    @Transactional
    public List<SampleView> forOrder(AuthUser caller, UUID orderId) {
        LabOrder order = orders.findById(orderId).orElseThrow(() -> notFound("Order not found"));
        List<Sample> list = samples.findByLabOrderIdOrderByCreatedAt(orderId);
        if (!list.isEmpty()) {
            checkCanSee(caller, list.getFirst());
        } else {
            checkCanSeeOrder(caller, order);
        }
        return list.stream().map(s -> view(s, caller.role())).toList();
    }

    @Transactional(readOnly = true)
    public List<SampleView> mine(AuthUser patient) {
        UUID patientId = patients.findByUserId(patient.id()).map(Patient::getId)
                .orElseThrow(() -> notFound("No patient record for this account"));
        return samples.findTop50ByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(s -> view(s, Role.PATIENT)).toList();
    }

    /** The lab's lists: samples to collect or to receive, urgent and redraws first. */
    @Transactional(readOnly = true)
    public PageResponse<SampleSummary> waiting(SampleStatus status, int page, int size) {
        return new PageResponse<>(queries.waiting(status, size, page * size), page, size, queries.countWaiting(status));
    }

    // ---------------------------------------------------------------- views and helpers

    /**
     * Builds the view for a role. Lab staff and pathologists see who did each step and the rejection
     * detail; everyone else sees the journey without names, and patients get no rejection reason.
     */
    SampleView view(Sample sample, Role role) {
        boolean staff = role == Role.LAB_TECHNICIAN || role == Role.PATHOLOGIST || role == Role.ADMIN;
        LabOrder order = orders.findById(sample.getLabOrderId()).orElseThrow();
        Patient p = patients.findById(sample.getPatientId()).orElseThrow();
        Map<UUID, LabOrderItem> items = order.getItems().stream().collect(Collectors.toMap(LabOrderItem::getId, Function.identity()));
        Map<UUID, LabTest> catalog = tests.findByIdIn(items.values().stream().map(LabOrderItem::getLabTestId).toList())
                .stream().collect(Collectors.toMap(LabTest::getId, Function.identity()));
        List<SampleTest> included = sample.getItemIds().stream().map(items::get)
                .sorted(java.util.Comparator.comparing(LabOrderItem::getTestName))
                .map(i -> new SampleTest(i.getId(), catalog.get(i.getLabTestId()).getCode(), i.getTestName())).toList();

        String redrawOf = sample.getRedrawOfSampleId() == null ? null
                : samples.findById(sample.getRedrawOfSampleId()).map(Sample::getSampleCode).orElse(null);
        Optional<Sample> replacement = sample.getStatus() == SampleStatus.REJECTED
                ? samples.findByRedrawOfSampleId(sample.getId()) : Optional.empty();

        Rejection rejection = null;
        if (staff && sample.getStatus() == SampleStatus.REJECTED) {
            rejection = rejections.findBySampleId(sample.getId()).map(r -> {
                UserRef by = queries.users(List.of(r.getFlaggedByUserId())).get(r.getFlaggedByUserId());
                return new Rejection(r.getStage(), r.getReason(), r.getNote(), r.getFlaggedAt(), by == null ? null : by.name());
            }).orElse(null);
        }
        return new SampleView(sample.getId(), sample.getSampleCode(), sample.getStatus(), sample.getRequiredTubeType(),
                sample.getTubeTypeUsed(), sample.isTubeMismatch(), sample.getBodySite(), sample.getCreatedAt(),
                sample.getCollectedAt(), sample.getReceivedAt(), order.getId(), order.getOrderCode(), order.getPriority(),
                new SamplePatient(p.getId(), p.getPatientCode(), p.getFullName(), p.ageOn(time.today()), p.getGender().name()),
                included, redrawOf, replacement.map(Sample::getId).orElse(null),
                replacement.map(Sample::getSampleCode).orElse(null), rejection, eventViews(sample.getId(), staff));
    }

    private List<SampleEvent> eventViews(UUID sampleId, boolean withNames) {
        List<SampleStatusEvent> log = events.findBySampleIdOrderByOccurredAt(sampleId);
        Map<UUID, UserRef> who = withNames
                ? queries.users(log.stream().map(SampleStatusEvent::getActorUserId).distinct().toList()) : Map.of();
        return log.stream().map(e -> {
            UserRef actor = who.get(e.getActorUserId());
            return new SampleEvent(e.getStatus(), e.getOccurredAt(), actor == null ? null : actor.name(),
                    actor == null ? null : actor.role(), withNames ? e.getDetail() : null);
        }).toList();
    }

    private void record(Sample sample, SampleStatus status, UUID actorUserId, String detail) {
        events.save(new SampleStatusEvent(sample.getId(), status, actorUserId, time.now(), detail));
    }

    /** Lab staff and the front desk see any sample; a patient their own; a doctor those of their patients (logged). */
    private void checkCanSee(AuthUser caller, Sample sample) {
        switch (caller.role()) {
            case LAB_TECHNICIAN, PATHOLOGIST, RECEPTIONIST, ADMIN -> {
            }
            case PATIENT -> {
                UUID own = patients.findByUserId(caller.id()).map(Patient::getId).orElse(null);
                if (!sample.getPatientId().equals(own)) {
                    throw notFound("Sample not found");
                }
            }
            case DOCTOR -> {
                Doctor me = doctors.findByUserId(caller.id()).orElseThrow(() -> notFound("Sample not found"));
                LabOrder order = orders.findById(sample.getLabOrderId()).orElseThrow();
                boolean orderedByMe = order.getOrderingDoctorId().equals(me.getId());
                if (!orderedByMe && patients.findWithCareRelationship(sample.getPatientId(), me.getId()).isEmpty()) {
                    throw notFound("Sample not found");
                }
                accessLog.save(new PatientAccessLog(sample.getPatientId(), caller.id(), Resource.LAB_HISTORY, sample.getId()));
            }
        }
    }

    private void checkCanSeeOrder(AuthUser caller, LabOrder order) {
        switch (caller.role()) {
            case LAB_TECHNICIAN, PATHOLOGIST, RECEPTIONIST, ADMIN -> {
            }
            case PATIENT -> {
                UUID own = patients.findByUserId(caller.id()).map(Patient::getId).orElse(null);
                if (!order.getPatientId().equals(own)) {
                    throw notFound("Order not found");
                }
            }
            case DOCTOR -> {
                Doctor me = doctors.findByUserId(caller.id()).orElseThrow(() -> notFound("Order not found"));
                if (!order.getOrderingDoctorId().equals(me.getId())
                        && patients.findWithCareRelationship(order.getPatientId(), me.getId()).isEmpty()) {
                    throw notFound("Order not found");
                }
            }
        }
    }

    private Sample find(UUID id) {
        return samples.findById(id).orElseThrow(() -> notFound("Sample not found"));
    }

    static String describe(SampleStatus status) {
        return switch (status) {
            case ORDERED -> "waiting to be collected";
            case COLLECTED -> "collected and waiting for the receipt check";
            case RECEIVED_AT_LAB -> "at the lab";
            case IN_TESTING -> "in testing";
            case RESULT_ENTERED -> "waiting for verification";
            case VERIFIED -> "verified";
            case REPORT_GENERATED -> "reported";
            case DISPATCHED -> "dispatched";
            case REJECTED -> "rejected";
            case CANCELLED -> "cancelled";
        };
    }

    static String describe(Reason reason) {
        return switch (reason) {
            case HEMOLYZED -> "hemolyzed";
            case CLOTTED -> "clotted";
            case INSUFFICIENT_VOLUME -> "not enough volume";
            case SAMPLE_EXHAUSTED -> "sample used up";
            case SAMPLE_DEGRADED -> "sample degraded";
            case OTHER -> "other reason";
        };
    }

    static String label(TubeType tube) {
        return switch (tube) {
            case EDTA -> "EDTA";
            case PLAIN -> "plain";
            case SST -> "SST";
            case CITRATE -> "citrate";
            case FLUORIDE -> "fluoride";
            case HEPARIN -> "heparin";
            case URINE_CUP -> "urine cup";
            case STOOL_CUP -> "stool cup";
            case SWAB_TUBE -> "swab";
        };
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
