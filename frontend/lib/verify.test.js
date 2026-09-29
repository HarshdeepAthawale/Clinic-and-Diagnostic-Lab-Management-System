import { describe, expect, it } from 'vitest';
import { isReportCode, verifyPath } from './verify';

describe('isReportCode', () => {
  it('accepts exactly 32 lowercase hex characters', () => {
    expect(isReportCode('0123456789abcdef0123456789abcdef')).toBe(true);
    expect(isReportCode('a'.repeat(32))).toBe(true);
  });

  it('rejects anything else, so no request is made for it', () => {
    expect(isReportCode('a'.repeat(31))).toBe(false);
    expect(isReportCode('a'.repeat(33))).toBe(false);
    expect(isReportCode('A'.repeat(32))).toBe(false);
    expect(isReportCode('g'.repeat(32))).toBe(false);
    expect(isReportCode("' OR 1=1--")).toBe(false);
    expect(isReportCode('')).toBe(false);
    expect(isReportCode(undefined)).toBe(false);
  });
});

describe('verifyPath', () => {
  it('is the address printed under the QR code', () => {
    expect(verifyPath('abc')).toBe('/verify/abc');
  });
});
