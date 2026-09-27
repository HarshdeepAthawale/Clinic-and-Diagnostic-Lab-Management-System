package com.cdlms.appointment;

import com.cdlms.appointment.AppointmentDtos.AppointmentDetail;
import com.cdlms.appointment.AppointmentDtos.AppointmentView;
import com.cdlms.appointment.AppointmentDtos.BookRequest;
import com.cdlms.appointment.AppointmentDtos.DaySlots;
import com.cdlms.appointment.AppointmentDtos.DoctorOption;
import com.cdlms.appointment.AppointmentDtos.DoctorRef;
import com.cdlms.appointment.AppointmentDtos.QueueBoard;
import com.cdlms.appointment.AppointmentDtos.QueueColumn;
import com.cdlms.appointment.AppointmentDtos.StatusRequest;
import com.cdlms.appointment.AppointmentDtos.TokenRequest;
import com.cdlms.appointment.AppointmentDtos.WorkingBlock;
import com.cdlms.appointment.AppointmentDtos.WorkingHours;
import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.common.ClinicTime;
import com.cdlms.patient.Patient;
import com.cdlms.patient.PatientRepository;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import com.cdlms.user.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Booking, walk-in tokens, the status lifecycle and the live queue (Docs/Rules.md §1a, ADR-020).
 * Every status change is checked against {@link AppointmentStatus} and written to the append-only
 * {@code appointment_events} history in the same transaction.
 */
@Service
public class AppointmentService {

    /** How far ahead a slot can be booked. */
    static final Duration BOOKING_HORIZON = Duration.ofDays(60);
    /** Longest window a list request may cover (a month view). */
    static final long MAX_LIST_DAYS = 42;

    private final AppointmentRepository appointments;
    private final AppointmentEventRepository events;
    private final AppointmentQueries queries;
    private final DoctorScheduleRepository schedules;
    private final SlotPlanner slots;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final ClinicTime time;

    public AppointmentService(AppointmentRepository appointments, AppointmentEventRepository events,
                              AppointmentQueries queries, DoctorScheduleRepository schedules, SlotPlanner slots,
                              PatientRepository patients, DoctorRepository doctors, ClinicTime time) {
        this.appointments = appointments;
        this.events = events;
        this.queries = queries;
        this.schedules = schedules;
        this.slots = slots;
        this.patients = patients;
        this.doctors = doctors;
        this.time = time;
    }

    // ---------------------------------------------------------------- doctors & slots

    @Transactional(readOnly = true)
    public List<DoctorOption> doctors() {
        return queries.doctors();
    }

    /** The calling doctor's own profile (for their schedule page). */
    @Transactional(readOnly = true)
    public DoctorOption me(AuthUser doctor) {
        UUID id = doctorIdOf(doctor);
        return queries.doctors().stream().filter(d -> d.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No doctor profile for this account"));
    }

    @Transactional(readOnly = true)
    public DaySlots slots(UUID doctorId, LocalDate date) {
        requireDoctor(doctorId);
        return slots.day(doctorId, date);
    }

    @Transactional(readOnly = true)
    public List<WorkingBlock> workingHours(UUID doctorId) {
        requireDoctor(doctorId);
        return schedules.findForDoctor(doctorId).stream()
                .map(s -> new WorkingBlock(s.getDayOfWeek().getValue(), s.getStartTime(), s.getEndTime(),
                        s.getSlotMinutes()))
                .toList();
    }

    /** Replaces a doctor's weekly hours. Existing bookings are kept even if they fall outside the new hours. */
    @Transactional
    public List<WorkingBlock> replaceWorkingHours(AuthUser caller, UUID doctorId, WorkingHours request) {
        requireDoctor(doctorId);
        if (caller.role() == Role.DOCTOR && !doctorId.equals(doctorIdOf(caller))) {
            throw forbidden();
        }
        List<WorkingBlock> blocks = request.blocks().stream()
                .sorted(Comparator.comparing(WorkingBlock::dayOfWeek).thenComparing(WorkingBlock::startTime))
                .toList();
        for (int i = 0; i < blocks.size(); i++) {
            WorkingBlock b = blocks.get(i);
            if (!b.endTime().isAfter(b.startTime())) {
                throw badRequest("INVALID_HOURS", "Each block must end after it starts");
            }
            if (i > 0 && blocks.get(i - 1).dayOfWeek().equals(b.dayOfWeek())
                    && blocks.get(i - 1).endTime().isAfter(b.startTime())) {
                throw badRequest("OVERLAPPING_HOURS", "Two blocks on " + DayOfWeek.of(b.dayOfWeek()) + " overlap");
            }
        }
        schedules.deleteForDoctor(doctorId);
        schedules.flush();
        schedules.saveAll(blocks.stream().map(b -> new DoctorSchedule(doctorId, DayOfWeek.of(b.dayOfWeek()),
                b.startTime(), b.endTime(), b.slotMinutes())).toList());
        return blocks;
    }

    // ---------------------------------------------------------------- booking & tokens

    @Transactional
    public AppointmentView book(AuthUser caller, BookRequest request) {
        UUID patientId = switch (caller.role()) {
            case PATIENT -> ownPatient(caller).getId();
            case RECEPTIONIST -> {
                if (request.patientId() == null) {
                    throw badRequest("PATIENT_REQUIRED", "Choose the patient to book for");
                }
                yield requirePatient(request.patientId()).getId();
            }
            default -> throw forbidden();
        };
        requireDoctor(request.doctorId());

        Instant now = time.now();
        Instant slot = request.scheduledAt();
        if (!slot.isAfter(now)) {
            throw badRequest("SLOT_IN_PAST", "That time has already passed");
        }
        if (slot.isAfter(now.plus(BOOKING_HORIZON))) {
            throw badRequest("TOO_FAR_AHEAD", "Appointments can be booked up to 60 days ahead");
        }
        DoctorSchedule block = slots.blockFor(request.doctorId(), slot)
                .orElseThrow(() -> badRequest("NOT_A_SLOT", "The doctor isn't seeing patients at that time"));
        LocalDate day = time.dateOf(slot);
        if (appointments.hasLiveBooking(patientId, request.doctorId(), time.startOf(day), time.startOf(day.plusDays(1)))) {
            throw conflict("ALREADY_BOOKED", "This patient already has an appointment with this doctor that day");
        }

        Appointment appointment = Appointment.booked(patientId, request.doctorId(), slot, block.getSlotMinutes(),
                blankToNull(request.reason()), caller.id());
        try {
            appointment = appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw conflict("SLOT_TAKEN", "Someone just booked that slot. Pick another time.");
        }
        record(appointment, null, caller, null);
        return view(appointment.getId());
    }

    /** Walk-in: a checked-in visit with the next token number, created at the front desk. */
    @Transactional
    public AppointmentView issueToken(AuthUser caller, TokenRequest request) {
        Patient patient = requirePatient(request.patientId());
        requireDoctor(request.doctorId());
        LocalDate today = time.today();
        if (appointments.hasLiveBooking(patient.getId(), request.doctorId(), time.startOf(today),
                time.startOf(today.plusDays(1)))) {
            throw conflict("ALREADY_BOOKED",
                    "This patient already has a visit with this doctor today. Check them in instead.");
        }
        appointments.lockQueueDay(today);
        Appointment appointment = Appointment.walkIn(patient.getId(), request.doctorId(), time.now(),
                blankToNull(request.reason()), caller.id(), today, appointments.nextQueueNumber(today));
        appointment = appointments.saveAndFlush(appointment);
        record(appointment, null, caller, null);
        return view(appointment.getId());
    }

    // ---------------------------------------------------------------- lifecycle

    @Transactional
    public AppointmentView changeStatus(AuthUser caller, UUID id, StatusRequest request) {
        Appointment appointment = appointments.findById(id).orElseThrow(AppointmentService::notFound);
        checkCanSee(caller, appointment);

        AppointmentStatus from = appointment.getStatus();
        AppointmentStatus to = request.status();
        if (!from.canMoveTo(to)) {
            throw conflict("INVALID_TRANSITION", "Can't change a " + label(from) + " appointment to " + label(to));
        }
        if (!from.allowedBy(to, caller.role())) {
            throw forbidden();
        }

        Instant now = time.now();
        switch (to) {
            case CHECKED_IN -> {
                if (!time.dateOf(appointment.getScheduledAt()).equals(time.today())) {
                    throw conflict("NOT_TODAY", "Patients can only be checked in on the day of their appointment");
                }
                LocalDate today = time.today();
                appointments.lockQueueDay(today);
                appointment.assignToken(today, appointments.nextQueueNumber(today));
            }
            case IN_CONSULTATION -> {
                if (appointments.existsByDoctorIdAndStatus(appointment.getDoctorId(), AppointmentStatus.IN_CONSULTATION)) {
                    throw conflict("ALREADY_IN_CONSULTATION", "Finish your current consultation first");
                }
            }
            case NO_SHOW -> {
                if (from == AppointmentStatus.BOOKED && appointment.getScheduledAt().isAfter(now)) {
                    throw conflict("TOO_EARLY", "The appointment time hasn't come yet");
                }
            }
            case CANCELLED -> {
                if (caller.role() == Role.PATIENT && !appointment.getScheduledAt().isAfter(now)) {
                    throw conflict("TOO_LATE", "This appointment has already started. Call the clinic to cancel.");
                }
            }
            default -> {
            }
        }

        String note = blankToNull(request.note());
        appointment.moveTo(to, now, note);
        appointments.saveAndFlush(appointment);
        record(appointment, from, caller, note);
        return view(id);
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public AppointmentDetail detail(AuthUser caller, UUID id) {
        Appointment appointment = appointments.findById(id).orElseThrow(AppointmentService::notFound);
        checkCanSee(caller, appointment);
        return new AppointmentDetail(view(id), queries.history(id));
    }

    /** Day or range view. Doctors always see only their own; the front desk and admin may filter by doctor. */
    @Transactional(readOnly = true)
    public List<AppointmentView> list(AuthUser caller, LocalDate from, LocalDate to, UUID doctorId) {
        LocalDate start = from == null ? time.today() : from;
        LocalDate end = to == null ? start : to;
        if (end.isBefore(start) || Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() >= MAX_LIST_DAYS) {
            throw badRequest("INVALID_RANGE", "Choose a range of up to " + MAX_LIST_DAYS + " days");
        }
        UUID doctor = caller.role() == Role.DOCTOR ? doctorIdOf(caller) : doctorId;
        return queries.list(time.startOf(start), time.startOf(end.plusDays(1)), doctor, null,
                caller.role() != Role.DOCTOR);
    }

    @Transactional(readOnly = true)
    public Map<String, List<AppointmentView>> mine(AuthUser caller) {
        UUID patientId = ownPatient(caller).getId();
        Instant since = time.startOf(time.today());
        Map<String, List<AppointmentView>> result = new LinkedHashMap<>();
        result.put("upcoming", queries.forPatient(patientId, since, true, 50));
        result.put("past", queries.forPatient(patientId, since, false, 50));
        return result;
    }

    /** Today's live queue, one column per doctor who has tokens today (or just the calling doctor). */
    @Transactional(readOnly = true)
    public QueueBoard queue(AuthUser caller) {
        LocalDate today = time.today();
        UUID onlyDoctor = caller.role() == Role.DOCTOR ? doctorIdOf(caller) : null;
        Map<UUID, List<AppointmentView>> byDoctor = new LinkedHashMap<>();
        if (onlyDoctor != null) {
            byDoctor.put(onlyDoctor, new ArrayList<>());
        }
        for (AppointmentView a : queries.queue(today, onlyDoctor)) {
            byDoctor.computeIfAbsent(a.doctor().id(), k -> new ArrayList<>()).add(a);
        }
        List<QueueColumn> columns = new ArrayList<>();
        byDoctor.forEach((doctorId, rows) -> {
            DoctorRef doctor = rows.isEmpty() ? queries.doctor(doctorId).orElseThrow() : rows.getFirst().doctor();
            columns.add(new QueueColumn(doctor,
                    rows.stream().filter(a -> a.status() == AppointmentStatus.IN_CONSULTATION).findFirst().orElse(null),
                    rows.stream().filter(a -> a.status() == AppointmentStatus.CHECKED_IN).toList(),
                    rows.stream().filter(a -> a.status() == AppointmentStatus.COMPLETED).count(),
                    rows.stream().filter(a -> a.status() == AppointmentStatus.NO_SHOW).count()));
        });
        columns.sort(Comparator.comparing(c -> c.doctor().fullName()));
        return new QueueBoard(today, time.now(), columns);
    }

    // ---------------------------------------------------------------- helpers

    private void record(Appointment appointment, AppointmentStatus from, AuthUser by, String note) {
        events.save(new AppointmentEvent(appointment.getId(), from, appointment.getStatus(), by.id(), note, time.now()));
    }

    private AppointmentView view(UUID id) {
        return queries.find(id).orElseThrow(AppointmentService::notFound);
    }

    /** Patients see their own; doctors their own; front desk and admin all. */
    private void checkCanSee(AuthUser caller, Appointment appointment) {
        boolean allowed = switch (caller.role()) {
            case PATIENT -> ownPatient(caller).getId().equals(appointment.getPatientId());
            case DOCTOR -> doctorIdOf(caller).equals(appointment.getDoctorId());
            case RECEPTIONIST, ADMIN -> true;
            default -> false;
        };
        if (!allowed) {
            // Same answer as "doesn't exist", so IDs can't be probed.
            throw notFound();
        }
    }

    private Patient ownPatient(AuthUser caller) {
        return patients.findByUserId(caller.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No patient record for this account"));
    }

    private Patient requirePatient(UUID patientId) {
        return patients.findById(patientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND", "Patient not found"));
    }

    private void requireDoctor(UUID doctorId) {
        if (!doctors.existsById(doctorId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND", "Doctor not found");
        }
    }

    private UUID doctorIdOf(AuthUser doctor) {
        return doctors.findByUserId(doctor.id()).map(Doctor::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No doctor profile for this account"));
    }

    private static String label(AppointmentStatus status) {
        return status.name().toLowerCase().replace('_', ' ');
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Appointment not found");
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource");
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
