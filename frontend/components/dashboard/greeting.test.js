import { describe, expect, it } from 'vitest';
import { initials } from '@/components/shell/UserMenu';
import { greeting, shortName } from './greeting';

describe('greeting', () => {
  it('follows the time of day', () => {
    expect(greeting(new Date(2026, 8, 27, 8))).toBe('Good morning');
    expect(greeting(new Date(2026, 8, 27, 13))).toBe('Good afternoon');
    expect(greeting(new Date(2026, 8, 27, 20))).toBe('Good evening');
  });
});

describe('names', () => {
  it('keeps the Dr. title in short names and drops it from initials', () => {
    expect(shortName('Dr. Kabir Mehta')).toBe('Dr. Kabir');
    expect(shortName('Asha Rao')).toBe('Asha');
    expect(initials('Dr. Kabir Mehta')).toBe('KM');
    expect(initials('Priya')).toBe('P');
    expect(initials('')).toBe('?');
  });
});
