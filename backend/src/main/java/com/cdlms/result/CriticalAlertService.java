package com.cdlms.result;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.common.ClinicTime;
import com.cdlms.lab.LabOrder;
import com.cdlms.lab.LabOrderRepository;
import com.cdlms.patient.PatientAccessLog;
import com.cdlms.patient.PatientAccessLog.Resource;
import com.cdlms.patient.PatientAccessLogRepository;
import com.cdlms.result.ResultDtos.Acknowledgement;
import com.cdlms.result.ResultDtos.CriticalAlerts;
import com.cdlms.result.ResultDtos.CriticalSummary;
import com.cdlms.sample.Sample;
import com.cdlms.sample.SampleRepository;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Critical value alerts (ADR-028). A verified report with a value at a critical limit reaches the doctor
 * who ordered it straight away — on their dashboard and as a count on the bell — and stays there until they
 * acknowledge it. The lab sees the same list (it may need to phone the doctor); the admin sees only how many
 * are waiting and for how long, because admins don't see reports.
 *
 * <p>An acknowledgement is one conditional update, so it is recorded once, by one person, even if two
 * requests arrive together. It never blocks anything: dispatching the report to the patient is separate.
 */
@Service
public class CriticalAlertService {

    static final int LIST_LIMIT = 20;
    static final int NOTE_MAX = 300;

    private final ResultQueries queries;
    private final ReportRepository reports;
    private final SampleRepository samples;
    private final LabOrderRepository orders;
    private final DoctorRepository doctors;
    private final PatientAccessLogRepository accessLog;
    private final ClinicTime time;

    public CriticalAlertService(ResultQueries queries, ReportRepository reports, SampleRepository samples,
                                LabOrderRepository orders, DoctorRepository doctors, PatientAccessLogRepository accessLog,
                                ClinicTime time) {
        this.queries = queries;
        this.reports = reports;
        this.samples = samples;
        this.orders = orders;
        this.doctors = doctors;
        this.accessLog = accessLog;
        this.time = time;
    }

    /** The doctor's own unacknowledged critical results, oldest first. */
    @Transactional(readOnly = true)
    public CriticalAlerts forDoctor(AuthUser doctorUser) {
        UUID doctorId = doctorOf(doctorUser).getId();
        return new CriticalAlerts(queries.countCriticalOpen(doctorId), queries.criticalOpen(doctorId, LIST_LIMIT));
    }

    /** Every waiting critical result — for the lab, who may need to phone the doctor. */
    @Transactional(readOnly = true)
    public CriticalAlerts forLab() {
        return new CriticalAlerts(queries.countCriticalOpen(null), queries.criticalOpen(null, LIST_LIMIT));
    }

    /** For the admin: how many are waiting and since when. No patients, no tests. */
    @Transactional(readOnly = true)
    public CriticalSummary summary() {
        long open = queries.countCriticalOpen(null);
        return new CriticalSummary(open, open == 0 ? null : queries.oldestCriticalOpen());
    }

    /** The ordering doctor confirms they have seen the critical result. Once only. */
    @Transactional
    public Acknowledgement acknowledge(AuthUser doctorUser, UUID sampleId, String note) {
        Doctor doctor = doctorOf(doctorUser);
        Sample sample = samples.findById(sampleId).orElseThrow(CriticalAlertService::notFound);
        LabOrder order = orders.findById(sample.getLabOrderId()).orElseThrow(CriticalAlertService::notFound);
        Report report = reports.findBySampleId(sampleId).orElseThrow(CriticalAlertService::notFound);
        if (!order.getOrderingDoctorId().equals(doctor.getId())) {
            // Only the doctor who ordered it is answerable for it; anyone else sees nothing.
            throw notFound();
        }
        if (!report.isCritical()) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_CRITICAL", "This report has no critical value to acknowledge");
        }
        String trimmed = note == null || note.isBlank() ? null : note.trim();
        if (trimmed != null && trimmed.length() > NOTE_MAX) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Keep the note under " + NOTE_MAX + " characters");
        }
        Instant at = time.now();
        if (reports.acknowledgeCritical(sampleId, doctorUser.id(), trimmed, at) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ACKNOWLEDGED", "This critical result has already been acknowledged");
        }
        accessLog.save(new PatientAccessLog(sample.getPatientId(), doctorUser.id(), Resource.LAB_REPORT, sample.getId()));
        return new Acknowledgement(sampleId, at, doctor.getFullName());
    }

    private Doctor doctorOf(AuthUser user) {
        return doctors.findByUserId(user.id())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No doctor profile for this account"));
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Report not found");
    }
}
