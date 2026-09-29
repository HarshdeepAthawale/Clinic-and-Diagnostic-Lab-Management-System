package com.cdlms.result;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.common.ClinicTime;
import com.cdlms.common.PageResponse;
import com.cdlms.lab.LabOrder;
import com.cdlms.lab.LabOrderItem;
import com.cdlms.lab.LabOrderRepository;
import com.cdlms.lab.LabTest;
import com.cdlms.lab.LabTestRepository;
import com.cdlms.patient.Patient;
import com.cdlms.patient.PatientAccessLog;
import com.cdlms.patient.PatientAccessLog.Resource;
import com.cdlms.patient.PatientAccessLogRepository;
import com.cdlms.patient.PatientRepository;
import com.cdlms.result.ResultDtos.ReportRow;
import com.cdlms.result.ResultDtos.ReportTest;
import com.cdlms.result.ResultDtos.ReportView;
import com.cdlms.result.ResultDtos.ValueView;
import com.cdlms.result.ResultDtos.Verifier;
import com.cdlms.sample.Sample;
import com.cdlms.sample.SampleRepository;
import com.cdlms.sample.SampleStatus;
import com.cdlms.sample.SampleStatusEvent;
import com.cdlms.sample.SampleStatusEventRepository;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import com.cdlms.user.Pathologist;
import com.cdlms.user.PathologistRepository;
import com.cdlms.user.User;
import com.cdlms.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reports and their dispatch (Docs/Rules.md §2, ADR-025).
 *
 * <p>A report is created when a pathologist verifies a result. The lab technician then dispatches it —
 * by email (a notice that points to the patient's account) or as a download link; SMS stays unavailable
 * until a provider is chosen. A patient can open a report only after it has been dispatched, and the
 * first time they do it is recorded as receipt. Doctors see reports for their patients as soon as they
 * are verified. Nothing is stored as a file: the PDF is drawn from the verified values on request.
 */
@Service
public class ReportService {

    static final int LIST_LIMIT = 50;

    private final ReportRepository reports;
    private final SampleResultRepository results;
    private final SampleRepository samples;
    private final SampleStatusEventRepository events;
    private final LabOrderRepository orders;
    private final LabTestRepository tests;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final PathologistRepository pathologists;
    private final UserRepository users;
    private final PatientAccessLogRepository accessLog;
    private final ResultQueries queries;
    private final ReportMailer mailer;
    private final ClinicTime time;

    public ReportService(ReportRepository reports, SampleResultRepository results, SampleRepository samples,
                         SampleStatusEventRepository events, LabOrderRepository orders, LabTestRepository tests,
                         PatientRepository patients, DoctorRepository doctors, PathologistRepository pathologists,
                         UserRepository users, PatientAccessLogRepository accessLog, ResultQueries queries,
                         ReportMailer mailer, ClinicTime time) {
        this.reports = reports;
        this.results = results;
        this.samples = samples;
        this.events = events;
        this.orders = orders;
        this.tests = tests;
        this.patients = patients;
        this.doctors = doctors;
        this.pathologists = pathologists;
        this.users = users;
        this.accessLog = accessLog;
        this.queries = queries;
        this.mailer = mailer;
        this.time = time;
    }

    // ---------------------------------------------------------------- reading

    /** One report for the reader view and the PDF. Patients get it only once dispatched; opening it confirms receipt. */
    @Transactional
    public ReportView get(AuthUser caller, UUID sampleId) {
        Report report = reports.findBySampleId(sampleId).orElseThrow(() -> notFound());
        Sample sample = samples.findById(sampleId).orElseThrow(() -> notFound());
        checkCanRead(caller, sample, report);
        if (caller.role() == com.cdlms.user.Role.PATIENT && report.getReceiptConfirmedAt() == null
                && reports.confirmReceipt(sampleId, time.now()) > 0) {
            // First open: reload so the response shows the time just recorded.
            report = reports.findBySampleId(sampleId).orElseThrow(() -> notFound());
            sample = samples.findById(sampleId).orElseThrow(() -> notFound());
        }
        return view(sample, report);
    }

    @Transactional(readOnly = true)
    public List<ReportRow> mine(AuthUser patient) {
        return queries.dispatchedForPatient(ownPatientId(patient), LIST_LIMIT);
    }

    /** Verified reports for orders this doctor placed. */
    @Transactional(readOnly = true)
    public PageResponse<ReportRow> orderedByMe(AuthUser doctor, int page, int size) {
        UUID id = doctorOf(doctor).getId();
        return new PageResponse<>(queries.orderedBy(id, size, page * size), page, size, queries.countOrderedBy(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportRow> toDispatch(int page, int size) {
        return new PageResponse<>(queries.toDispatch(size, page * size), page, size, queries.countToDispatch());
    }

    // ---------------------------------------------------------------- dispatch

    /** Sends the patient their report: an email notice or a download link. Once only. */
    @Transactional
    public ReportView dispatch(AuthUser tech, UUID sampleId, Report.Channel channel) {
        Report report = reports.findBySampleId(sampleId).orElseThrow(() -> notFound());
        Sample sample = samples.findById(sampleId).orElseThrow(() -> notFound());
        if (report.isDispatched()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_DISPATCHED", "This report has already been dispatched");
        }
        if (channel == Report.Channel.SMS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHANNEL_UNAVAILABLE",
                    "SMS isn't set up yet — send it by email or as a download link");
        }
        Patient patient = patients.findById(sample.getPatientId()).orElseThrow();
        if (channel == Report.Channel.EMAIL) {
            String email = patient.getUserId() == null ? null : users.findById(patient.getUserId()).map(User::getEmail).orElse(null);
            if (email == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "NO_EMAIL",
                        patient.getFullName() + " has no account with an email address — use the download link instead");
            }
            mailer.send(new ReportMailer.Notice(email, patient.getFullName(), sample.getSampleCode(), testNames(sample)));
        }
        report.dispatch(channel, tech.id(), time.now());
        reports.saveAndFlush(report);

        sample.dispatched();
        samples.saveAndFlush(sample);
        events.save(new SampleStatusEvent(sample.getId(), SampleStatus.DISPATCHED, tech.id(), time.now(),
                channel == Report.Channel.EMAIL ? "Emailed to the patient" : "Made available to download"));
        return view(sample, report);
    }

    // ---------------------------------------------------------------- helpers

    private void checkCanRead(AuthUser caller, Sample sample, Report report) {
        switch (caller.role()) {
            case PATIENT -> {
                if (!sample.getPatientId().equals(ownPatientId(caller)) || !report.isDispatched()) {
                    throw notFound();
                }
            }
            case DOCTOR -> {
                Doctor me = doctorOf(caller);
                LabOrder order = orders.findById(sample.getLabOrderId()).orElseThrow();
                if (!order.getOrderingDoctorId().equals(me.getId())
                        && patients.findWithCareRelationship(sample.getPatientId(), me.getId()).isEmpty()) {
                    throw notFound();
                }
                accessLog.save(new PatientAccessLog(sample.getPatientId(), caller.id(), Resource.LAB_REPORT, sample.getId()));
            }
            case PATHOLOGIST -> accessLog.save(
                    new PatientAccessLog(sample.getPatientId(), caller.id(), Resource.LAB_REPORT, sample.getId()));
            case LAB_TECHNICIAN -> {
            }
            default -> throw notFound();
        }
    }

    ReportView view(Sample sample, Report report) {
        SampleResult verified = results.findFirstBySampleIdAndStatus(sample.getId(), SampleResult.Status.VERIFIED)
                .orElseThrow(() -> new IllegalStateException("Report " + report.getId() + " has no verified result"));
        LabOrder order = orders.findById(sample.getLabOrderId()).orElseThrow();
        Patient p = patients.findById(sample.getPatientId()).orElseThrow();
        Pathologist verifier = pathologists.findById(verified.getVerifiedByPathologistId()).orElseThrow();
        String doctorName = doctors.findById(order.getOrderingDoctorId()).map(Doctor::getFullName).orElse(null);

        Map<UUID, LabOrderItem> items = order.getItems().stream().collect(Collectors.toMap(LabOrderItem::getId, Function.identity()));
        Map<UUID, LabTest> catalog = tests.findByIdIn(items.values().stream().map(LabOrderItem::getLabTestId).toList())
                .stream().collect(Collectors.toMap(LabTest::getId, Function.identity()));
        Map<UUID, List<ValueView>> byItem = new LinkedHashMap<>();
        for (ValueView v : ResultService.valueViews(verified)) {
            byItem.computeIfAbsent(v.itemId(), k -> new java.util.ArrayList<>()).add(v);
        }
        List<ReportTest> reportTests = byItem.entrySet().stream()
                .map(e -> new ReportTest(catalog.get(items.get(e.getKey()).getLabTestId()).getCode(),
                        items.get(e.getKey()).getTestName(), e.getValue()))
                .sorted(java.util.Comparator.comparing(ReportTest::testName)).toList();

        return new ReportView(sample.getId(), sample.getSampleCode(), order.getId(), order.getOrderCode(),
                new ResultDtos.Patient(p.getId(), p.getPatientCode(), p.getFullName(), p.ageOn(time.today()), p.getGender().name()),
                doctorName, sample.getRequiredTubeType(), sample.getCollectedAt(), sample.getReceivedAt(),
                verified.getAttemptNumber(), reportTests,
                new Verifier(verifier.getFullName(), verifier.getQualification(), verifier.getRegistrationNumber()),
                verified.getVerifiedAt(), report.getGeneratedAt(), report.getDispatchedChannel(), report.getDispatchedAt(),
                report.getReceiptConfirmedAt(), verified.isCritical());
    }

    private List<String> testNames(Sample sample) {
        LabOrder order = orders.findById(sample.getLabOrderId()).orElseThrow();
        return order.getItems().stream().filter(i -> sample.getItemIds().contains(i.getId()))
                .map(LabOrderItem::getTestName).sorted().toList();
    }

    private Doctor doctorOf(AuthUser doctor) {
        return doctors.findByUserId(doctor.id())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No doctor profile for this account"));
    }

    private UUID ownPatientId(AuthUser patient) {
        return patients.findByUserId(patient.id()).map(Patient::getId).orElseThrow(() -> notFound());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Report not found");
    }
}
