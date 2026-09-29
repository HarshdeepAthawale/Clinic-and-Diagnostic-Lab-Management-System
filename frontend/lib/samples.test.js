import { describe, expect, it } from 'vitest';
import {
  collectionProblems,
  isBloodTube,
  JOURNEY_STAGES,
  journeyFor,
  normalizeSampleCode,
  plainStatus,
  qrRows,
} from './samples';

const at = (h) => `2026-09-29T0${h}:00:00Z`;

describe('journeyFor', () => {
  it('starts at Ordered with no finished stops', () => {
    expect(journeyFor({ events: [{ status: 'ORDERED', occurredAt: at(1) }] })).toEqual({ current: 0, times: [] });
  });

  it('marks earlier stops done with their times', () => {
    const j = journeyFor({
      events: [
        { status: 'ORDERED', occurredAt: at(1) },
        { status: 'COLLECTED', occurredAt: at(2) },
        { status: 'RECEIVED_AT_LAB', occurredAt: at(3) },
      ],
    });
    expect(j.current).toBe(2);
    expect(j.times).toEqual([at(1), at(2)]);
  });

  it('shows a retest and result entry as Testing, without going backwards', () => {
    const j = journeyFor({
      events: [
        { status: 'ORDERED', occurredAt: at(1) },
        { status: 'COLLECTED', occurredAt: at(2) },
        { status: 'RECEIVED_AT_LAB', occurredAt: at(3) },
        { status: 'IN_TESTING', occurredAt: at(4) },
        { status: 'RESULT_ENTERED', occurredAt: at(5) },
        { status: 'IN_TESTING', occurredAt: at(6) },
      ],
    });
    expect(j.current).toBe(3);
    expect(j.times[3]).toBeUndefined(); // the current stop has no time yet
    expect(JOURNEY_STAGES[j.current]).toBe('Testing');
  });

  it('leaves a rejected sample where it was', () => {
    const j = journeyFor({
      events: [
        { status: 'ORDERED', occurredAt: at(1) },
        { status: 'COLLECTED', occurredAt: at(2) },
        { status: 'REJECTED', occurredAt: at(3) },
      ],
    });
    expect(j.current).toBe(1);
  });

  it('copes with a sample that has no events', () => {
    expect(journeyFor({})).toEqual({ current: 0, times: [] });
  });
});

describe('plainStatus', () => {
  it('speaks plainly to patients', () => {
    expect(plainStatus('ORDERED')).toMatch(/taken/);
    expect(plainStatus('REJECTED')).toBe('A new sample is needed');
  });
});

describe('collectionProblems', () => {
  it('needs a body site for blood but not for a cup', () => {
    expect(collectionProblems({ tube: 'EDTA', site: '', required: 'EDTA', confirmed: false }).site).toMatch(/where/);
    expect(collectionProblems({ tube: 'EDTA', site: 'Left arm', required: 'EDTA', confirmed: false }).site).toBeNull();
    expect(collectionProblems({ tube: 'URINE_CUP', site: '', required: 'URINE_CUP', confirmed: false }).site).toBeNull();
  });

  it('flags a wrong tube until it is confirmed', () => {
    expect(collectionProblems({ tube: 'SST', site: 'Left arm', required: 'EDTA', confirmed: false }).mismatch).toBe('mismatch');
    expect(collectionProblems({ tube: 'SST', site: 'Left arm', required: 'EDTA', confirmed: true }).mismatch).toBeNull();
    expect(collectionProblems({ tube: 'EDTA', site: 'Left arm', required: 'EDTA', confirmed: false }).mismatch).toBeNull();
  });

  it('knows which containers are blood tubes', () => {
    expect(isBloodTube('FLUORIDE')).toBe(true);
    expect(isBloodTube('STOOL_CUP')).toBe(false);
  });
});

describe('normalizeSampleCode', () => {
  it('accepts codes however they are typed or scanned', () => {
    expect(normalizeSampleCode(' lab-20260929-0007 ')).toBe('LAB-20260929-0007');
    expect(normalizeSampleCode('LAB-20260929-0007\n')).toBe('LAB-20260929-0007');
    expect(normalizeSampleCode('lab - 20260929 - 0007')).toBe('LAB-20260929-0007');
  });

  it('rejects anything else', () => {
    expect(normalizeSampleCode('')).toBeNull();
    expect(normalizeSampleCode('PID-000001')).toBeNull();
    expect(normalizeSampleCode('LAB-2026-7')).toBeNull();
    expect(normalizeSampleCode(null)).toBeNull();
  });
});

describe('qrRows', () => {
  it('builds a square grid of modules for a sample code', () => {
    const rows = qrRows('LAB-20260929-0007');
    expect(rows.length).toBeGreaterThanOrEqual(21);
    expect(rows.every((r) => r.length === rows.length)).toBe(true);
    // Every QR code has the same finder pattern in its top-left corner.
    expect(rows[0].slice(0, 7).every(Boolean)).toBe(true);
    expect(rows[1][1]).toBe(false);
  });

  it('is the same every time and differs between codes', () => {
    expect(qrRows('LAB-20260929-0007')).toEqual(qrRows('LAB-20260929-0007'));
    expect(qrRows('LAB-20260929-0007')).not.toEqual(qrRows('LAB-20260929-0008'));
  });
});
