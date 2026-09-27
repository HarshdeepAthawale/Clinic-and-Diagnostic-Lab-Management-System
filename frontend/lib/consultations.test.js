import { describe, expect, it } from 'vitest';
import { describeFrequency, prescriptionPdfUrl } from './consultations';

describe('describeFrequency', () => {
  it('reads morning-afternoon-night shorthand', () => {
    expect(describeFrequency('1-0-1')).toBe('Morning, night');
    expect(describeFrequency('1-1-1')).toBe('Morning, afternoon, night');
    expect(describeFrequency('0-0-1')).toBe('Night');
  });

  it('leaves anything else alone', () => {
    expect(describeFrequency('SOS')).toBeNull();
    expect(describeFrequency('1-1-1-1')).toBeNull();
    expect(describeFrequency('0-0-0')).toBeNull();
    expect(describeFrequency(undefined)).toBeNull();
  });
});

it('builds the PDF url', () => {
  expect(prescriptionPdfUrl('abc')).toBe('/api/prescriptions/abc/pdf');
});
