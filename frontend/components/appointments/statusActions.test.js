import { describe, expect, it } from 'vitest';
import { statusActions } from './statusActions';

const now = new Date('2026-09-28T06:00:00Z'); // 11:30 in Kolkata
const at = (iso, status) => ({ status, scheduledAt: iso });
const labels = (actions) => actions.map((a) => a.status);

describe('statusActions', () => {
  it('front desk checks in today, marks no-shows only after the time', () => {
    expect(labels(statusActions('RECEPTIONIST', at('2026-09-28T08:00:00Z', 'BOOKED'), now))).toEqual(['CHECKED_IN', 'CANCELLED']);
    expect(labels(statusActions('RECEPTIONIST', at('2026-09-28T05:00:00Z', 'BOOKED'), now))).toEqual(['CHECKED_IN', 'NO_SHOW', 'CANCELLED']);
  });

  it('no check-in on another day', () => {
    expect(labels(statusActions('RECEPTIONIST', at('2026-09-29T08:00:00Z', 'BOOKED'), now))).toEqual(['CANCELLED']);
  });

  it('doctor starts and reopens the consultation from the workspace', () => {
    expect(labels(statusActions('DOCTOR', at('2026-09-28T05:00:00Z', 'CHECKED_IN'), now))).toEqual(['IN_CONSULTATION', 'NO_SHOW']);
    expect(labels(statusActions('DOCTOR', at('2026-09-28T05:00:00Z', 'IN_CONSULTATION'), now))).toEqual(['COMPLETED']);
    expect(statusActions('DOCTOR', at('2026-09-28T05:00:00Z', 'BOOKED'), now)).toEqual([]);
    expect(statusActions('DOCTOR', at('2026-09-28T05:00:00Z', 'CHECKED_IN'), now)[0]).toMatchObject({ consult: true, label: 'Start consultation' });
    expect(statusActions('DOCTOR', at('2026-09-28T05:00:00Z', 'IN_CONSULTATION'), now)[0]).toMatchObject({ consult: true, label: 'Open consultation' });
  });

  it('patients can only cancel future bookings', () => {
    expect(labels(statusActions('PATIENT', at('2026-09-29T08:00:00Z', 'BOOKED'), now))).toEqual(['CANCELLED']);
    expect(statusActions('PATIENT', at('2026-09-28T05:00:00Z', 'BOOKED'), now)).toEqual([]);
  });

  it('finished visits offer nothing', () => {
    expect(statusActions('RECEPTIONIST', at('2026-09-28T05:00:00Z', 'COMPLETED'), now)).toEqual([]);
  });
});
