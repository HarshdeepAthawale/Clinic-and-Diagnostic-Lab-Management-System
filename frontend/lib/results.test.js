import { describe, expect, it } from 'vitest';
import {
  entryBody,
  entryProblems,
  entrySummary,
  flagFor,
  flagLabel,
  flagStatus,
  formatRange,
  formatValue,
  isAbnormal,
  isCritical,
  trendSeries,
} from './results';

const hb = { parameterId: 'hb', name: 'Haemoglobin', valueType: 'NUMERIC', refLow: 12, refHigh: 15.5, criticalLow: 7, criticalHigh: 20 };
const wbc = { parameterId: 'wbc', name: 'WBC', valueType: 'NUMERIC', refLow: 4, refHigh: 11, criticalLow: 2, criticalHigh: 30 };
const ns1 = { parameterId: 'ns1', name: 'NS1', valueType: 'TEXT' };
const sheet = [{ itemId: 'i1', testCode: 'CBC', testName: 'CBC', parameters: [hb, wbc] }, { itemId: 'i2', testCode: 'NS1', testName: 'NS1', parameters: [ns1] }];

describe('flagFor', () => {
  it('matches the server: critical wins and is inclusive, then low/high, edges are normal', () => {
    expect(flagFor('13.2', hb)).toBe('NORMAL');
    expect(flagFor('12', hb)).toBe('NORMAL');
    expect(flagFor('15.5', hb)).toBe('NORMAL');
    expect(flagFor('11.9', hb)).toBe('LOW');
    expect(flagFor('15.6', hb)).toBe('HIGH');
    expect(flagFor('7', hb)).toBe('CRITICAL_LOW');
    expect(flagFor('20', hb)).toBe('CRITICAL_HIGH');
    expect(flagFor('6.5', hb)).toBe('CRITICAL_LOW');
  });

  it('ignores missing limits and empty or invalid input', () => {
    expect(flagFor('500', { refLow: 40 })).toBe('NORMAL');
    expect(flagFor('10', { refHigh: 200 })).toBe('NORMAL');
    expect(flagFor('250', { refHigh: 200 })).toBe('HIGH');
    expect(flagFor('', hb)).toBeNull();
    expect(flagFor('abc', hb)).toBeNull();
    expect(flagFor(null, hb)).toBeNull();
  });
});

describe('flag words and badges', () => {
  it('names flags and maps them to the status vocabulary', () => {
    expect(flagLabel('CRITICAL_HIGH')).toBe('Critical high');
    expect(flagLabel('NORMAL')).toBe('Normal');
    expect(flagLabel(null)).toBe('');
    expect(flagStatus('CRITICAL_LOW')).toBe('critical');
    expect(flagStatus('HIGH')).toBe('high');
    expect(flagStatus('LOW')).toBe('low');
    expect(flagStatus('NORMAL')).toBe('normal');
    expect(isCritical('CRITICAL_HIGH')).toBe(true);
    expect(isCritical('HIGH')).toBe(false);
    expect(isAbnormal('LOW')).toBe(true);
    expect(isAbnormal('NORMAL')).toBe(false);
    expect(isAbnormal(null)).toBe(false);
  });
});

describe('formatting', () => {
  it('writes ranges and values readably', () => {
    expect(formatRange(hb)).toBe('12 – 15.5');
    expect(formatRange({ refHigh: 200 })).toBe('< 200');
    expect(formatRange({ refLow: 40 })).toBe('> 40');
    expect(formatRange(ns1)).toBe('—');
    expect(formatValue({ valueType: 'NUMERIC', numericValue: 13.2 })).toBe('13.2');
    expect(formatValue({ valueType: 'NUMERIC', numericValue: '140.0000' })).toBe('140');
    expect(formatValue({ valueType: 'TEXT', textValue: 'Positive' })).toBe('Positive');
  });
});

describe('entry form', () => {
  it('needs a value for every parameter and numbers for numeric ones', () => {
    expect(entryProblems(sheet, { hb: '13', wbc: '7', ns1: 'Negative' })).toEqual({});
    expect(entryProblems(sheet, { hb: '13', wbc: '', ns1: '  ' })).toEqual({ wbc: 'Enter a value', ns1: 'Enter a value' });
    expect(entryProblems(sheet, { hb: 'lots', wbc: '7', ns1: 'x' })).toEqual({ hb: 'Must be a number' });
    expect(entryProblems(sheet, { hb: '13.23456', wbc: '7', ns1: 'x' })).toEqual({ hb: 'At most 4 decimals' });
  });

  it('builds the request in sheet order with trimmed values', () => {
    expect(entryBody(sheet, { hb: ' 13.2 ', wbc: '7', ns1: 'Negative' }, ' Sysmex ')).toEqual({
      analyzer: 'Sysmex',
      values: [
        { parameterId: 'hb', value: '13.2' },
        { parameterId: 'wbc', value: '7' },
        { parameterId: 'ns1', value: 'Negative' },
      ],
    });
    expect(entryBody(sheet, { hb: '1', wbc: '1', ns1: 'x' }, '').analyzer).toBeNull();
  });

  it('summarises how much is out of range while typing', () => {
    expect(entrySummary(sheet, { hb: '6', wbc: '12', ns1: 'Negative' })).toEqual({ abnormal: 2, critical: 1 });
    expect(entrySummary(sheet, { hb: '13', wbc: '7', ns1: 'Negative' })).toEqual({ abnormal: 0, critical: 0 });
    expect(entrySummary(sheet, {})).toEqual({ abnormal: 0, critical: 0 });
  });
});

describe('trendSeries', () => {
  it('puts earlier values oldest first and ends with today', () => {
    const points = [{ value: 12 }, { value: 11 }, { value: 10.5 }]; // newest first from the server
    expect(trendSeries(points, '12.5')).toEqual([10.5, 11, 12, 12.5]);
    expect(trendSeries(undefined, 9)).toEqual([9]);
  });
});
