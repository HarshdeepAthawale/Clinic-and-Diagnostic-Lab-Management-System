import { describe, expect, it } from 'vitest';
import { addDays, clinicDate, dayLabel, minutesWaiting, weekStart } from './appointments';

describe('clinic dates', () => {
  it('uses the clinic time zone for "today"', () => {
    // 20:00 UTC is already 01:30 the next day in Kolkata.
    expect(clinicDate(0, new Date('2026-09-27T20:00:00Z'))).toBe('2026-09-28');
    expect(clinicDate(1, new Date('2026-09-27T10:00:00Z'))).toBe('2026-09-28');
  });

  it('adds days across month ends', () => {
    expect(addDays('2026-09-30', 1)).toBe('2026-10-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('finds the Monday of a week', () => {
    expect(weekStart('2026-09-27')).toBe('2026-09-21'); // Sunday
    expect(weekStart('2026-09-28')).toBe('2026-09-28'); // Monday
    expect(weekStart('2026-10-01')).toBe('2026-09-28'); // Thursday
  });

  it('labels a day', () => {
    expect(dayLabel('2026-09-28')).toBe('Mon 28 Sept');
  });

  it('counts minutes waited, never negative', () => {
    const now = Date.parse('2026-09-27T10:30:00Z');
    expect(minutesWaiting('2026-09-27T10:12:30Z', now)).toBe(17);
    expect(minutesWaiting('2026-09-27T10:31:00Z', now)).toBe(0);
  });
});
