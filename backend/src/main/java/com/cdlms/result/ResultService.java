package com.cdlms.result;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.common.ClinicTime;
import com.cdlms.common.PageResponse;
import com.cdlms.lab.LabOrder;
import com.cdlms.lab.LabOrderItem;
import com.cdlms.lab.LabOrderRepository;
import com.cdlms.lab.LabTest;
import com.cdlms.lab.LabTestParameter;
import com.cdlms.lab.LabTestRepository;
import com.cdlms.lab.ValueType;
import com.cdlms.patient.Patient;
import com.cdlms.patient.PatientAccessLog;
import com.cdlms.patient.PatientAccessLog.Resource;
import com.cdlms.patient.PatientAccessLogRepository;
import com.cdlms.patient.PatientRepository;
import com.cdlms.result.RangeCheck.Flag;
import com.cdlms.result.ResultDtos.Attempt;
import com.cdlms.result.ResultDtos.EnterResultsRequest;
import com.cdlms.result.ResultDtos.ParameterSpec;
import com.cdlms.result.ResultDtos.RejectRequest;
import com.cdlms.result.ResultDtos.ReturnRequest;
import com.cdlms.result.ResultDtos.SampleResults;
import com.cdlms.result.ResultDtos.TestSheet;
import com.cdlms.result.ResultDtos.TestingRow;
import com.cdlms.result.ResultDtos.TrendPoint;
import com.cdlms.result.ResultDtos.ValueEntry;
import com.cdlms.result.ResultDtos.ValueView;
import com.cdlms.result.ResultDtos.VerificationRow;
import com.cdlms.result.SampleResult.ReturnReason;
import com.cdlms.sample.RejectionRecord.Stage;
import com.cdlms.sample.Sample;
import com.cdlms.sample.SampleQueries;
import com.cdlms.sample.SampleQueries.UserRef;
import com.cdlms.sample.SampleRepository;
import com.cdlms.sample.SampleService;
import com.cdlms.sample.SampleStatus;
import com.cdlms.sample.SampleStatusEvent;
import com.cdlms.sample.SampleStatusEventRepository;
import com.cdlms.user.Pathologist;
import com.cdlms.user.PathologistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Testing, results and the pathologist's verification (Docs/Rules.md §2, ADR-025).
 *
 * <p>An accepted sample goes into testing; the technician enters a value for every parameter of every
 * test on it, and the server flags each number against its range. The pathologist then verifies the
 * result — which is the only way to a report — or returns it for a retest, keeping the attempt as
 * history. A technician can also reject a sample during testing (used up or degraded), which reuses the
 * receipt-check rejection: permanent record, front-desk call-back, redraw.
 */
@Service
public class ResultService {

    static final int TREND_POINTS = 4;

    private final SampleRepository samples;
    private final SampleStatusEventRepository events;
    private final SampleResultRepository results;
    private final ReportRepository reports;
    private final ResultQueries queries;
    private final SampleQueries sampleQueries;
    private final SampleService sampleService;
    private final LabOrderRepository orders;
    private final LabTestRepository tests;
    private final PatientRepository patients;
    private final PathologistRepository pathologists;
    private final PatientAccessLogRepository accessLog;
    private final ClinicTime time;

    public ResultService(SampleRepository samples, SampleStatusEventRepository events, SampleResultRepository results,
                         ReportRepository reports, ResultQueries queries, SampleQueries sampleQueries,
                         SampleService sampleService, LabOrderRepository orders, LabTestRepository tests,
                         PatientRepository patients, PathologistRepository pathologists,
                         PatientAccessLogRepository accessLog, ClinicTime time) {
        this.samples = samples;
        this.events = events;
        this.results = results;
        this.reports = reports;
        this.queries = queries;
        this.sampleQueries = sampleQueries;
        this.sampleService = sampleService;
        this.orders = orders;
        this.tests = tests;
        this.patients = patients;
        this.pathologists = pathologists;
        this.accessLog = accessLog;
        this.time = time;
    }

    // ---------------------------------------------------------------- lab technician

    /** Puts an accepted sample into testing. */
    @Transactional
    public SampleResults startTesting(AuthUser tech, UUID sampleId) {
        Sample sample = find(sampleId);
        if (sample.getStatus() != SampleStatus.RECEIVED_AT_LAB) {
            throw conflict("NOT_READY_FOR_TESTING", "This sample is " + describe(sample.getStatus()) + ", so testing can't start");
        }
        sample.startTesting();
        samples.saveAndFlush(sample);
        record(sample, SampleStatus.IN_TESTING, tech.id(), "Testing started");
        return view(sample, tech, false);
    }

    /**
     * Enters one attempt: a value for every parameter of every test on the sample, no more and no less.
     * Numbers are flagged against the parameter's range as it stands now; the ranges are copied onto the
     * value so the flag keeps its meaning. The sample then waits for the pathologist.
     */
    @Transactional
    public SampleResults enterResults(AuthUser tech, UUID sampleId, EnterResultsRequest request) {
        Sample sample = find(sampleId);
        if (sample.getStatus() != SampleStatus.IN_TESTING) {
            throw conflict("NOT_IN_TESTING", "Results can only be entered while a sample is in testing — this one is "
                    + describe(sample.getStatus()));
        }

        List<TestSheet> sheet = sheet(sample);
        Map<UUID, ValueEntry> given = new HashMap<>();
        for (ValueEntry entry : request.values()) {
            if (given.put(entry.parameterId(), entry) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_VALUE", "A value was sent twice for the same parameter");
            }
        }
        Set<UUID> expected = new HashSet<>();
        sheet.forEach(t -> t.parameters().forEach(p -> expected.add(p.parameterId())));
        if (!expected.containsAll(given.keySet())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PARAMETER", "A value doesn't belong to any test on this sample");
        }
        List<String> missing = sheet.stream().flatMap(t -> t.parameters().stream())
                .filter(p -> !given.containsKey(p.parameterId())).map(ParameterSpec::name).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_VALUES", "Enter a value for every parameter — missing: "
                    + String.join(", ", missing));
        }

        SampleResult attempt = new SampleResult(sampleId, results.countBySampleId(sampleId) + 1, trim(request.analyzer()),
                tech.id(), time.now());
        int position = 0;
        boolean critical = false;
        int abnormal = 0;
        for (TestSheet test : sheet) {
            for (ParameterSpec spec : test.parameters()) {
                position++;
                ValueEntry entry = given.get(spec.parameterId());
                BigDecimal numeric = null;
                String text = null;
                Flag flag = null;
                if (spec.valueType() == ValueType.NUMERIC) {
                    numeric = parseNumber(spec, entry.value());
                    flag = RangeCheck.flag(numeric, spec.refLow(), spec.refHigh(), spec.criticalLow(), spec.criticalHigh());
                    critical |= RangeCheck.isCritical(flag);
                    abnormal += RangeCheck.isAbnormal(flag) ? 1 : 0;
                } else {
                    text = entry.value().trim();
                }
                attempt.addValue(new ResultValue.Entry(test.itemId(), spec.parameterId(), position, spec.name(), spec.unit(),
                        spec.valueType(), numeric, text, spec.refLow(), spec.refHigh(), spec.criticalLow(),
                        spec.criticalHigh(), flag));
            }
        }
        results.saveAndFlush(attempt);

        sample.resultEntered();
        samples.saveAndFlush(sample);
        record(sample, SampleStatus.RESULT_ENTERED, tech.id(), "Attempt " + attempt.getAttemptNumber()
                + (attempt.getAnalyzer() == null ? "" : " · " + attempt.getAnalyzer())
                + (critical ? " — CRITICAL VALUE" : abnormal > 0 ? " — " + abnormal + " out of range" : ""));
        return view(sample, tech, false);
    }

    /** Rejects a sample that is in testing (used up, degraded, or something else with a note). */
    @Transactional
    public SampleResults rejectInTesting(AuthUser tech, UUID sampleId, RejectRequest request) {
        Sample sample = find(sampleId);
        if (sample.getStatus() != SampleStatus.IN_TESTING) {
            throw conflict("NOT_IN_TESTING", "Only a sample in testing can be rejected here — this one is " + describe(sample.getStatus()));
        }
        sampleService.reject(sample, Stage.IN_TESTING, request.reason(), request.note(), tech.id());
        return view(sample, tech, false);
    }

    // ---------------------------------------------------------------- pathologist

    /**
     * Signs off the result. This is the gate to the report: it is the only thing that creates one, and the
     * database refuses a report without a verified result. The person who entered the result can't verify it.
     */
    @Transactional
    public SampleResults verify(AuthUser user, UUID sampleId) {
        Pathologist pathologist = pathologistOf(user);
        Sample sample = find(sampleId);
        SampleResult pending = pendingFor(sample);
        if (pending.getEnteredByUserId().equals(user.id())) {
            throw conflict("CANNOT_VERIFY_OWN", "You entered this result, so someone else has to verify it");
        }
        pending.verify(pathologist.getId(), time.now());
        results.saveAndFlush(pending);

        sample.verified();
        samples.saveAndFlush(sample);
        record(sample, SampleStatus.VERIFIED, user.id(), "Verified by " + pathologist.getFullName());

        reports.saveAndFlush(new Report(sample.getId(), user.id(), time.now(), pending.isCritical()));
        sample.reportGenerated();
        samples.saveAndFlush(sample);
        record(sample, SampleStatus.REPORT_GENERATED, user.id(), "Report generated");
        return view(sample, user, true);
    }

    /**
     * Sends the result back: the same sample is retested, the old attempt stays as history, and the
     * retest becomes the next attempt. Needs a reason (and a note for {@code OTHER}).
     */
    @Transactional
    public SampleResults returnForRetest(AuthUser user, UUID sampleId, ReturnRequest request) {
        Pathologist pathologist = pathologistOf(user);
        Sample sample = find(sampleId);
        SampleResult pending = pendingFor(sample);
        String note = trim(request.note());
        if (request.reason() == ReturnReason.OTHER && note == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTE_REQUIRED", "Add a note explaining why it is being returned");
        }
        pending.returnForRetest(pathologist.getId(), request.reason(), note, time.now());
        results.saveAndFlush(pending);

        sample.returnToTesting();
        samples.saveAndFlush(sample);
        record(sample, SampleStatus.IN_TESTING, user.id(), "Returned for retest — " + describe(request.reason())
                + (note == null ? "" : ": " + note));
        return view(sample, user, true);
    }

    // ---------------------------------------------------------------- reads

    /** The entry sheet and every attempt so far. The pathologist also gets the patient's earlier values. */
    @Transactional
    public SampleResults get(AuthUser caller, UUID sampleId) {
        Sample sample = find(sampleId);
        boolean pathologist = caller.role() == com.cdlms.user.Role.PATHOLOGIST;
        if (pathologist) {
            accessLog.save(new PatientAccessLog(sample.getPatientId(), caller.id(), Resource.LAB_REPORT, sample.getId()));
        }
        return view(sample, caller, pathologist);
    }

    @Transactional(readOnly = true)
    public PageResponse<TestingRow> toTest(int page, int size) {
        return new PageResponse<>(queries.toTest(size, page * size), page, size, queries.countToTest());
    }

    @Transactional(readOnly = true)
    public PageResponse<VerificationRow> pendingVerification(int page, int size) {
        return new PageResponse<>(queries.pendingVerification(size, page * size), page, size, queries.countPendingVerification());
    }

    @Transactional(readOnly = true)
    public PageResponse<VerificationRow> verifiedByMe(AuthUser user, int page, int size) {
        UUID id = pathologistOf(user).getId();
        return new PageResponse<>(queries.verifiedBy(id, size, page * size), page, size, queries.countVerifiedBy(id));
    }

    // ---------------------------------------------------------------- helpers

    /** The tests on the sample with the parameters to fill in, in a stable order. */
    List<TestSheet> sheet(Sample sample) {
        LabOrder order = orders.findById(sample.getLabOrderId()).orElseThrow();
        Map<UUID, LabOrderItem> items = order.getItems().stream()
                .filter(i -> sample.getItemIds().contains(i.getId()))
                .collect(Collectors.toMap(LabOrderItem::getId, Function.identity()));
        Map<UUID, LabTest> catalog = tests.findByIdIn(items.values().stream().map(LabOrderItem::getLabTestId).toList())
                .stream().collect(Collectors.toMap(LabTest::getId, Function.identity()));
        return items.values().stream()
                .sorted(Comparator.comparing(LabOrderItem::getTestName).thenComparing(LabOrderItem::getId))
                .map(item -> {
                    LabTest test = catalog.get(item.getLabTestId());
                    List<ParameterSpec> parameters = test.getParameters().stream()
                            .sorted(Comparator.comparingInt(LabTestParameter::getPosition))
                            .map(p -> new ParameterSpec(p.getId(), p.getPosition(), p.getName(), p.getUnit(), p.getValueType(),
                                    p.getRefLow(), p.getRefHigh(), p.getCriticalLow(), p.getCriticalHigh()))
                            .toList();
                    return new TestSheet(item.getId(), test.getCode(), item.getTestName(), parameters);
                }).toList();
    }

    private SampleResults view(Sample sample, AuthUser caller, boolean withTrend) {
        LabOrder order = orders.findById(sample.getLabOrderId()).orElseThrow();
        Patient p = patients.findById(sample.getPatientId()).orElseThrow();
        List<SampleResult> attempts = results.findBySampleIdOrderByAttemptNumber(sample.getId());

        Set<UUID> userIds = new HashSet<>();
        Set<UUID> pathologistIds = new HashSet<>();
        for (SampleResult r : attempts) {
            userIds.add(r.getEnteredByUserId());
            if (r.getVerifiedByPathologistId() != null) {
                pathologistIds.add(r.getVerifiedByPathologistId());
            }
            if (r.getReturnedByPathologistId() != null) {
                pathologistIds.add(r.getReturnedByPathologistId());
            }
        }
        Map<UUID, UserRef> users = sampleQueries.users(userIds);
        Map<UUID, String> pathologistNames = pathologists.findAllById(pathologistIds).stream()
                .collect(Collectors.toMap(Pathologist::getId, Pathologist::getFullName));

        List<Attempt> attemptViews = attempts.stream().map(r -> new Attempt(r.getId(), r.getAttemptNumber(), r.getStatus(),
                r.getAnalyzer(), name(users.get(r.getEnteredByUserId())), r.getEnteredAt(),
                pathologistNames.get(r.getVerifiedByPathologistId()), r.getVerifiedAt(),
                pathologistNames.get(r.getReturnedByPathologistId()), r.getReturnedAt(), r.getReturnReason(),
                r.getReturnNote(), r.isCritical(), valueViews(r))).toList();

        SampleResult lastReturned = attempts.stream().filter(r -> r.getStatus() == SampleResult.Status.RETURNED_FOR_RETEST)
                .reduce((a, b) -> b).orElse(null);

        Map<UUID, List<TrendPoint>> trend = null;
        if (withTrend && !attempts.isEmpty()) {
            SampleResult latest = attempts.getLast();
            List<UUID> numeric = latest.getValues().stream().filter(v -> v.getValueType() == ValueType.NUMERIC)
                    .map(ResultValue::getParameterId).toList();
            trend = new LinkedHashMap<>(queries.trend(sample.getPatientId(), numeric, sample.getId(), TREND_POINTS));
        }
        return new SampleResults(sample.getId(), sample.getSampleCode(), sample.getStatus(), sample.getRequiredTubeType(),
                order.getId(), order.getOrderCode(), order.getPriority(),
                new ResultDtos.Patient(p.getId(), p.getPatientCode(), p.getFullName(), p.ageOn(time.today()), p.getGender().name()),
                sample.getCollectedAt(), sample.getReceivedAt(), sheet(sample), attemptViews,
                (int) attempts.stream().filter(r -> r.getStatus() == SampleResult.Status.RETURNED_FOR_RETEST).count(),
                lastReturned == null ? null : lastReturned.getReturnReason(),
                lastReturned == null ? null : lastReturned.getReturnNote(), trend);
    }

    static List<ValueView> valueViews(SampleResult r) {
        return r.getValues().stream().map(v -> new ValueView(v.getLabOrderItemId(), v.getParameterId(), v.getPosition(),
                v.getParameterName(), v.getUnit(), v.getValueType(), v.getNumericValue(), v.getTextValue(), v.getRefLow(),
                v.getRefHigh(), v.getCriticalLow(), v.getCriticalHigh(), v.getFlag())).toList();
    }

    private static BigDecimal parseNumber(ParameterSpec spec, String raw) {
        BigDecimal value;
        try {
            value = new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VALUE", spec.name() + " must be a number");
        }
        if (value.scale() > 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VALUE", spec.name() + " can have at most 4 decimal places");
        }
        if (value.abs().compareTo(new BigDecimal("9999999999")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VALUE", spec.name() + " is too large");
        }
        return value;
    }

    private SampleResult pendingFor(Sample sample) {
        if (sample.getStatus() != SampleStatus.RESULT_ENTERED) {
            throw conflict("NOT_AWAITING_VERIFICATION", "This sample is " + describe(sample.getStatus())
                    + ", so there is no result waiting for you");
        }
        return results.findFirstBySampleIdAndStatus(sample.getId(), SampleResult.Status.PENDING_VERIFICATION)
                .orElseThrow(() -> conflict("NOT_AWAITING_VERIFICATION", "There is no result waiting for verification"));
    }

    private Pathologist pathologistOf(AuthUser user) {
        return pathologists.findByUserId(user.id())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No pathologist profile for this account"));
    }

    private void record(Sample sample, SampleStatus status, UUID actorUserId, String detail) {
        events.save(new SampleStatusEvent(sample.getId(), status, actorUserId, time.now(), detail));
    }

    private Sample find(UUID id) {
        return samples.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sample not found"));
    }

    private static String name(UserRef ref) {
        return ref == null ? null : ref.name();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static String describe(SampleStatus status) {
        return switch (status) {
            case ORDERED -> "waiting to be collected";
            case COLLECTED -> "waiting for the receipt check";
            case RECEIVED_AT_LAB -> "at the lab, not yet in testing";
            case IN_TESTING -> "in testing";
            case RESULT_ENTERED -> "waiting for verification";
            case VERIFIED -> "verified";
            case REPORT_GENERATED -> "reported";
            case DISPATCHED -> "dispatched";
            case REJECTED -> "rejected";
            case CANCELLED -> "cancelled";
        };
    }

    private static String describe(ReturnReason reason) {
        return switch (reason) {
            case IMPLAUSIBLE_VALUE -> "implausible value";
            case INCONSISTENT_WITH_HISTORY -> "inconsistent with the patient's history";
            case CRITICAL_VALUE_CONFIRMATION -> "critical value needs confirmation";
            case QC_CONCERN -> "quality-control concern";
            case OTHER -> "other reason";
        };
    }
}
