package com.cdlms.patient;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.common.PageResponse;
import com.cdlms.patient.PatientAccessLog.Resource;
import com.cdlms.patient.PatientDtos.PatientDetails;
import com.cdlms.patient.PatientDtos.PatientRecord;
import com.cdlms.patient.PatientDtos.PatientSummary;
import com.cdlms.patient.PatientDtos.RegisterPatientRequest;
import com.cdlms.patient.PatientDtos.RegisteredPatient;
import com.cdlms.patient.PatientDtos.UpdateClinicalRequest;
import com.cdlms.patient.PatientDtos.UpdateDemographicsRequest;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import com.cdlms.user.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The shared patient record and who may see what (Rules.md §1, ADR-015):
 * <ul>
 *   <li>Search: doctors and front desk; summary only, phone masked.</li>
 *   <li>Demographics: the patient themself, front desk, admin.</li>
 *   <li>Full record: the patient themself, and doctors with a care relationship — every doctor read
 *       is written to the access log in the same transaction.</li>
 * </ul>
 */
@Service
public class PatientService {

    private final PatientRepository patients;
    private final PatientAccessLogRepository accessLog;
    private final DoctorRepository doctors;
    private final ClaimCodes claimCodes;
    private final Clock clock;

    public PatientService(PatientRepository patients, PatientAccessLogRepository accessLog, DoctorRepository doctors,
                          ClaimCodes claimCodes) {
        this.patients = patients;
        this.accessLog = accessLog;
        this.doctors = doctors;
        this.claimCodes = claimCodes;
        this.clock = Clock.systemDefaultZone();
    }

    // ---------------------------------------------------------------- search

    @Transactional(readOnly = true)
    public PageResponse<PatientSummary> search(AuthUser caller, String query, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        Page<Patient> page;
        if (q.isEmpty()) {
            page = patients.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            String digits = q.replaceAll("\\D", "");
            page = patients.search("%" + q.toLowerCase(Locale.ROOT) + "%", q.toUpperCase(Locale.ROOT),
                    digits, "%" + digits + "%", pageable);
        }

        Set<UUID> careSet = caller.role() == Role.DOCTOR && !page.isEmpty()
                ? new HashSet<>(patients.careRelationshipsAmong(doctorId(caller),
                page.getContent().stream().map(Patient::getId).toList()))
                : Set.of();
        LocalDate today = LocalDate.now(clock);
        return PageResponse.of(page, p -> new PatientSummary(p.getId(), p.getPatientCode(), p.getFullName(),
                p.ageOn(today), p.getGender(), maskPhone(p.getPhone()),
                caller.role() == Role.DOCTOR ? careSet.contains(p.getId()) : null));
    }

    // ---------------------------------------------------------------- registration (front desk)

    @Transactional
    public RegisteredPatient register(AuthUser receptionist, RegisterPatientRequest request) {
        Patient patient = new Patient(null, request.fullName().trim(), request.dob(), request.gender(),
                request.phone().trim());
        patient.updateDemographics(patient.getFullName(), patient.getDob(), patient.getGender(), patient.getPhone(),
                blankToNull(request.address()), blankToNull(request.emergencyContactName()),
                blankToNull(request.emergencyContactPhone()));
        patient.updateClinical(blankToNull(request.knownAllergies()), blankToNull(request.medicalHistory()),
                blankToNull(request.bloodGroup()));
        patient.setRegisteredByUserId(receptionist.id());

        String code = claimCodes.generate();
        Instant expiresAt = Instant.now(clock).plus(ClaimCodes.VALIDITY);
        patient.issueClaimCode(claimCodes.hash(code), expiresAt);
        patient = patients.saveAndFlush(patient);
        return new RegisteredPatient(toDetails(patient), code, expiresAt);
    }

    /** Issues a fresh registration code (e.g. the slip was lost). Only for records without a login. */
    @Transactional
    public RegisteredPatient reissueRegistrationCode(UUID patientId) {
        Patient patient = find(patientId);
        if (patient.getUserId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_LINKED", "This patient already has a login");
        }
        String code = claimCodes.generate();
        Instant expiresAt = Instant.now(clock).plus(ClaimCodes.VALIDITY);
        patient.issueClaimCode(claimCodes.hash(code), expiresAt);
        return new RegisteredPatient(toDetails(patient), code, expiresAt);
    }

    // ---------------------------------------------------------------- demographics

    @Transactional(readOnly = true)
    public PatientDetails details(AuthUser caller, UUID patientId) {
        Patient patient = find(patientId);
        if (caller.role() == Role.PATIENT && !caller.id().equals(patient.getUserId())) {
            throw forbidden();
        }
        return toDetails(patient);
    }

    @Transactional
    public PatientDetails updateDemographics(UUID patientId, UpdateDemographicsRequest request) {
        Patient patient = find(patientId);
        patient.updateDemographics(request.fullName().trim(), request.dob(), request.gender(), request.phone().trim(),
                blankToNull(request.address()), blankToNull(request.emergencyContactName()),
                blankToNull(request.emergencyContactPhone()));
        return toDetails(patients.saveAndFlush(patient));
    }

    // ---------------------------------------------------------------- full record (EMR)

    @Transactional
    public PatientRecord record(AuthUser caller, UUID patientId) {
        Patient patient = switch (caller.role()) {
            case PATIENT -> {
                Patient own = find(patientId);
                if (!caller.id().equals(own.getUserId())) {
                    throw forbidden();
                }
                yield own;
            }
            case DOCTOR -> loadForDoctor(caller, patientId);
            default -> throw forbidden();
        };
        return toRecord(patient);
    }

    @Transactional(readOnly = true)
    public PatientRecord myRecord(AuthUser patientUser) {
        return toRecord(patients.findByUserId(patientUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No patient record for this account")));
    }

    @Transactional
    public PatientRecord updateClinical(AuthUser doctor, UUID patientId, UpdateClinicalRequest request) {
        Patient patient = loadForDoctor(doctor, patientId);
        patient.updateClinical(blankToNull(request.knownAllergies()), blankToNull(request.medicalHistory()),
                blankToNull(request.bloodGroup()));
        return toRecord(patients.saveAndFlush(patient));
    }

    /** Care-relationship check inside the query, then an access-log row in the same transaction. */
    private Patient loadForDoctor(AuthUser doctor, UUID patientId) {
        Patient patient = patients.findWithCareRelationship(patientId, doctorId(doctor)).orElseThrow(() -> {
            if (!patients.existsById(patientId)) {
                return notFound();
            }
            return new ApiException(HttpStatus.FORBIDDEN, "NO_CARE_RELATIONSHIP",
                    "You don't have an appointment with this patient");
        });
        accessLog.save(new PatientAccessLog(patient.getId(), doctor.id(), Resource.EMR, null));
        return patient;
    }

    // ---------------------------------------------------------------- helpers

    private UUID doctorId(AuthUser doctor) {
        return doctors.findByUserId(doctor.id()).map(Doctor::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No doctor profile for this account"));
    }

    private Patient find(UUID patientId) {
        return patients.findById(patientId).orElseThrow(PatientService::notFound);
    }

    private PatientDetails toDetails(Patient p) {
        return new PatientDetails(p.getId(), p.getPatientCode(), p.getFullName(), p.getDob(),
                p.ageOn(LocalDate.now(clock)), p.getGender(), p.getPhone(), p.getAddress(),
                p.getEmergencyContactName(), p.getEmergencyContactPhone(), p.getUserId() != null,
                p.getClaimCodeExpiresAt(), p.getCreatedAt());
    }

    private PatientRecord toRecord(Patient p) {
        return new PatientRecord(p.getId(), p.getPatientCode(), p.getFullName(), p.getDob(),
                p.ageOn(LocalDate.now(clock)), p.getGender(), p.getPhone(), p.getAddress(),
                p.getEmergencyContactName(), p.getEmergencyContactPhone(), p.getBloodGroup(),
                p.getKnownAllergies(), p.getMedicalHistory(), p.getUpdatedAt());
    }

    /** "+91 98765 43210" → "91********10": enough to confirm identity, not to call. */
    static String maskPhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() < 5) {
            return "****";
        }
        return digits.substring(0, 2) + "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 2);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Patient not found");
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource");
    }
}
