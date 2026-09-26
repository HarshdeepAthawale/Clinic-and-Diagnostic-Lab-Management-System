import { describe, expect, it } from 'vitest';
import { ageGender, APPOINTMENT_STATUS, formatRelative } from './format';

describe('formatRelative', () => {
  const now = Date.parse('2026-09-27T10:00:00Z');

  it('uses minutes and hours for recent times, then the date', () => {
    expect(formatRelative('2026-09-27T09:59:45Z', now)).toBe('just now');
    expect(formatRelative('2026-09-27T09:48:00Z', now)).toBe('12 min ago');
    expect(formatRelative('2026-09-27T07:00:00Z', now)).toBe('3 h ago');
    expect(formatRelative('2026-09-20T07:00:00Z', now)).toBe('20 Sept 2026');
  });
});

describe('ageGender', () => {
  it('abbreviates gender', () => {
    expect(ageGender(32, 'FEMALE')).toBe('32 · F');
    expect(ageGender(8, 'MALE')).toBe('8 · M');
    expect(ageGender(40, 'OTHER')).toBe('40 · Other');
  });
});

describe('APPOINTMENT_STATUS', () => {
  it('maps every backend appointment status to a status badge', () => {
    for (const status of ['BOOKED', 'CHECKED_IN', 'IN_CONSULTATION', 'COMPLETED', 'NO_SHOW', 'CANCELLED']) {
      expect(APPOINTMENT_STATUS[status]).toBeTruthy();
    }
  });
});
