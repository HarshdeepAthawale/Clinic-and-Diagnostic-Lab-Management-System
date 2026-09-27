import { clinicDate } from '@/lib/appointments';
import { CLINIC_TIME_ZONE } from '@/lib/format';

/**
 * Which status changes a role can offer for an appointment (mirrors AppointmentStatus.java and
 * Rules.md §1a). The server still decides; this only keeps impossible buttons off the screen.
 * Actions with `consult: true` don't change the status directly: they open the consult workspace
 * (starting the consultation calls the patient in; finishing it completes the visit — Phase 04).
 */
export function statusActions(role, appointment, now = new Date()) {
  const { status, scheduledAt } = appointment;
  const started = new Date(scheduledAt) <= now;
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: CLINIC_TIME_ZONE }).format(new Date(scheduledAt)) === clinicDate(0, now);

  if (role === 'RECEPTIONIST') {
    if (status === 'BOOKED') {
      return [
        ...(today ? [{ status: 'CHECKED_IN', label: 'Check in', primary: true }] : []),
        ...(started ? [{ status: 'NO_SHOW', label: 'Mark no-show' }] : []),
        { status: 'CANCELLED', label: 'Cancel appointment', danger: true, askReason: true },
      ];
    }
    if (status === 'CHECKED_IN') {
      return [
        { status: 'NO_SHOW', label: 'Left without being seen' },
        { status: 'CANCELLED', label: 'Cancel visit', danger: true, askReason: true },
      ];
    }
  }
  if (role === 'DOCTOR') {
    if (status === 'CHECKED_IN') {
      return [
        { status: 'IN_CONSULTATION', label: 'Start consultation', primary: true, consult: true },
        { status: 'NO_SHOW', label: 'Mark no-show' },
      ];
    }
    if (status === 'IN_CONSULTATION') return [{ status: 'COMPLETED', label: 'Open consultation', primary: true, consult: true }];
  }
  if (role === 'PATIENT' && status === 'BOOKED' && !started) {
    return [{ status: 'CANCELLED', label: 'Cancel appointment', danger: true, askReason: true }];
  }
  return [];
}
