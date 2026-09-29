import { describe, expect, it } from 'vitest';
import { isOverdue, parametersText, parameterText, waitingFor } from './critical';

const NOW = new Date('2026-09-29T12:00:00Z').getTime();
const ago = (minutes) => new Date(NOW - minutes * 60_000).toISOString();

describe('parameterText', () => {
  it('names the value and its direction but not the number', () => {
    expect(parameterText({ name: 'Haemoglobin', flag: 'CRITICAL_LOW' })).toBe('Haemoglobin — critical low');
    expect(parameterText({ name: 'Potassium', flag: 'CRITICAL_HIGH' })).toBe('Potassium — critical high');
  });

  it('joins several', () => {
    expect(parametersText([{ name: 'Hb', flag: 'CRITICAL_LOW' }, { name: 'K', flag: 'CRITICAL_HIGH' }])).toBe(
      'Hb — critical low; K — critical high',
    );
  });
});

describe('waitingFor', () => {
  it('reads in minutes, hours and days', () => {
    expect(waitingFor(ago(0), NOW)).toBe('0 min');
    expect(waitingFor(ago(5), NOW)).toBe('5 min');
    expect(waitingFor(ago(60), NOW)).toBe('1 h');
    expect(waitingFor(ago(130), NOW)).toBe('2 h 10 min');
    expect(waitingFor(ago(24 * 60), NOW)).toBe('1 d');
    expect(waitingFor(ago(27 * 60), NOW)).toBe('1 d 3 h');
  });

  it('never goes negative when clocks disagree', () => {
    expect(waitingFor(new Date(NOW + 60_000).toISOString(), NOW)).toBe('0 min');
  });
});

describe('isOverdue', () => {
  it('is overdue after an hour without an acknowledgement', () => {
    expect(isOverdue(ago(30), NOW)).toBe(false);
    expect(isOverdue(ago(60), NOW)).toBe(false);
    expect(isOverdue(ago(61), NOW)).toBe(true);
  });
});
