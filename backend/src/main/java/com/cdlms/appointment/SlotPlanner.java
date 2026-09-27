package com.cdlms.appointment;

import com.cdlms.appointment.AppointmentDtos.DaySlots;
import com.cdlms.appointment.AppointmentDtos.Slot;
import com.cdlms.common.ClinicTime;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cuts a doctor's working hours into bookable slots for one day. A slot is available when it starts
 * in the future and no live booking holds it.
 */
@Component
public class SlotPlanner {

    private final DoctorScheduleRepository schedules;
    private final AppointmentQueries queries;
    private final ClinicTime time;

    public SlotPlanner(DoctorScheduleRepository schedules, AppointmentQueries queries, ClinicTime time) {
        this.schedules = schedules;
        this.queries = queries;
        this.time = time;
    }

    public DaySlots day(UUID doctorId, LocalDate date) {
        List<DoctorSchedule> blocks = schedules.findForDay(doctorId, (short) date.getDayOfWeek().getValue());
        if (blocks.isEmpty()) {
            return new DaySlots(doctorId, date, false, 0, List.of());
        }
        Set<Instant> taken = queries.takenSlots(doctorId, time.startOf(date), time.startOf(date.plusDays(1)));
        Instant now = time.now();
        List<Slot> slots = new ArrayList<>();
        for (DoctorSchedule block : blocks) {
            for (Instant start : starts(block, date)) {
                slots.add(new Slot(start, start.isAfter(now) && !taken.contains(start)));
            }
        }
        return new DaySlots(doctorId, date, true, blocks.getFirst().getSlotMinutes(), slots);
    }

    /** The working block a slot start belongs to, if {@code start} is exactly a slot boundary. */
    public Optional<DoctorSchedule> blockFor(UUID doctorId, Instant start) {
        LocalDate date = time.dateOf(start);
        return schedules.findForDay(doctorId, (short) date.getDayOfWeek().getValue()).stream()
                .filter(block -> starts(block, date).contains(start))
                .findFirst();
    }

    private List<Instant> starts(DoctorSchedule block, LocalDate date) {
        List<Instant> starts = new ArrayList<>();
        LocalTime t = block.getStartTime();
        // Stop at the last slot that still ends within the block (and don't wrap past midnight).
        while (!t.plusMinutes(block.getSlotMinutes()).isAfter(block.getEndTime())
                && t.plusMinutes(block.getSlotMinutes()).isAfter(t)) {
            starts.add(date.atTime(t).atZone(time.zone()).toInstant());
            t = t.plusMinutes(block.getSlotMinutes());
        }
        return starts;
    }
}
