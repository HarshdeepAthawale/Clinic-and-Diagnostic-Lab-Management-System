package com.cdlms.appointment;

import com.cdlms.user.Role;

import java.util.Map;
import java.util.Set;

/**
 * Appointment lifecycle (Docs/Rules.md §1a):
 * <pre>
 * BOOKED ──check in──▶ CHECKED_IN ──call in──▶ IN_CONSULTATION ──finish──▶ COMPLETED
 *   │                     │
 *   ├─▶ CANCELLED         ├─▶ CANCELLED
 *   └─▶ NO_SHOW           └─▶ NO_SHOW  (left before being seen)
 * </pre>
 * {@link #allowedBy(AppointmentStatus, Role)} is the single source of truth for who may make each
 * move; extra conditions (same day, own appointment, one patient at a time) live in the service.
 */
public enum AppointmentStatus {
    BOOKED, CHECKED_IN, IN_CONSULTATION, COMPLETED, NO_SHOW, CANCELLED;

    private static final Map<AppointmentStatus, Map<AppointmentStatus, Set<Role>>> TRANSITIONS = Map.of(
            BOOKED, Map.of(
                    CHECKED_IN, Set.of(Role.RECEPTIONIST),
                    CANCELLED, Set.of(Role.RECEPTIONIST, Role.PATIENT),
                    NO_SHOW, Set.of(Role.RECEPTIONIST)),
            CHECKED_IN, Map.of(
                    IN_CONSULTATION, Set.of(Role.DOCTOR),
                    CANCELLED, Set.of(Role.RECEPTIONIST),
                    NO_SHOW, Set.of(Role.RECEPTIONIST, Role.DOCTOR)),
            IN_CONSULTATION, Map.of(
                    COMPLETED, Set.of(Role.DOCTOR)));

    /** Whether {@code target} can follow this status at all, regardless of who asks. */
    public boolean canMoveTo(AppointmentStatus target) {
        return TRANSITIONS.getOrDefault(this, Map.of()).containsKey(target);
    }

    public boolean allowedBy(AppointmentStatus target, Role role) {
        return TRANSITIONS.getOrDefault(this, Map.of()).getOrDefault(target, Set.of()).contains(role);
    }

    /** Still going to happen or happening now — counts as live for slots and "today" views. */
    public boolean isActive() {
        return this == BOOKED || this == CHECKED_IN || this == IN_CONSULTATION;
    }
}
