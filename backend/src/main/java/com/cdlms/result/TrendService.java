package com.cdlms.result;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.patient.Patient;
import com.cdlms.patient.PatientAccessLog;
import com.cdlms.patient.PatientAccessLog.Resource;
import com.cdlms.patient.PatientAccessLogRepository;
import com.cdlms.patient.PatientRepository;
import com.cdlms.result.ResultDtos.PatientTrends;
import com.cdlms.result.ResultDtos.TrendSeries;
import com.cdlms.result.ResultDtos.TrendValue;
import com.cdlms.result.ResultQueries.TrendRow;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A patient's numbers across visits (ADR-029), built from the verified results they already have — nothing
 * new is stored. Patients see values from reports that have been sent to them; a doctor needs a care
 * relationship (and is logged, as for any lab history); pathologists see all verified values.
 */
@Service
public class TrendService {

    /** Enough to see the direction without a chart full of ancient history. */
    static final int MAX_POINTS = 12;
    /** A trend needs at least two visits. */
    static final int MIN_POINTS = 2;

    private final ResultQueries queries;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final PatientAccessLogRepository accessLog;

    public TrendService(ResultQueries queries, PatientRepository patients, DoctorRepository doctors,
                        PatientAccessLogRepository accessLog) {
        this.queries = queries;
        this.patients = patients;
        this.doctors = doctors;
        this.accessLog = accessLog;
    }

    @Transactional
    public PatientTrends forPatient(AuthUser caller, UUID patientId) {
        boolean dispatchedOnly = switch (caller.role()) {
            case PATIENT -> {
                Patient own = patients.findByUserId(caller.id()).orElseThrow(TrendService::notFound);
                if (!own.getId().equals(patientId)) {
                    // Someone else's record looks exactly like one that doesn't exist.
                    throw notFound();
                }
                yield true;
            }
            case DOCTOR -> {
                Doctor me = doctors.findByUserId(caller.id())
                        .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No doctor profile for this account"));
                if (!patients.existsById(patientId)) {
                    throw notFound();
                }
                if (patients.findWithCareRelationship(patientId, me.getId()).isEmpty()) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "NO_CARE_RELATIONSHIP", "You don't have an appointment with this patient");
                }
                accessLog.save(new PatientAccessLog(patientId, caller.id(), Resource.LAB_HISTORY, null));
                yield false;
            }
            case PATHOLOGIST -> {
                if (!patients.existsById(patientId)) {
                    throw notFound();
                }
                accessLog.save(new PatientAccessLog(patientId, caller.id(), Resource.LAB_HISTORY, null));
                yield false;
            }
            default -> throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource");
        };
        return new PatientTrends(patientId, series(queries.trendRows(patientId, dispatchedOnly)));
    }

    /** Groups rows by parameter, keeps the latest {@link #MAX_POINTS}, drops parameters measured fewer than twice. */
    static List<TrendSeries> series(List<TrendRow> rows) {
        Map<UUID, List<TrendRow>> byParameter = new LinkedHashMap<>();
        for (TrendRow row : rows) {
            byParameter.computeIfAbsent(row.parameterId(), k -> new ArrayList<>()).add(row);
        }
        List<TrendSeries> out = new ArrayList<>();
        for (List<TrendRow> group : byParameter.values()) {
            if (group.size() < MIN_POINTS) {
                continue;
            }
            List<TrendRow> recent = group.subList(Math.max(0, group.size() - MAX_POINTS), group.size());
            TrendRow latest = recent.getLast();
            out.add(new TrendSeries(latest.parameterId(), latest.parameterName(), latest.unit(), latest.testCode(), latest.testName(),
                    latest.refLow(), latest.refHigh(), latest.criticalLow(), latest.criticalHigh(),
                    recent.stream().map(r -> new TrendValue(r.sampleId(), r.sampleCode(), r.verifiedAt(), r.value(), r.flag())).toList()));
        }
        return out;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Patient not found");
    }
}
