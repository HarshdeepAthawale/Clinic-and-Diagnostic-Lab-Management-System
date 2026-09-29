import { describe, expect, it } from 'vitest';
import { chartRows, groupByTest, latestFlag, referenceLines, trendChange, yDomain } from './trends';

const point = (value, day, flag = 'NORMAL') => ({ value, flag, sampleCode: `LAB-${day}`, verifiedAt: `2026-01-${String(day).padStart(2, '0')}T09:30:00Z` });
const hb = (values) => ({
  name: 'Haemoglobin', unit: 'g/dL', testCode: 'CBC', testName: 'CBC', refLow: 12, refHigh: 15.5, criticalLow: 7, criticalHigh: 20,
  points: values.map((v, i) => point(v, i + 1)),
});

describe('chartRows', () => {
  it('turns points into a day and a number', () => {
    expect(chartRows(hb([10, '12.6']))).toEqual([
      { date: '2026-01-01', value: 10, sampleCode: 'LAB-1' },
      { date: '2026-01-02', value: 12.6, sampleCode: 'LAB-2' },
    ]);
  });
});

describe('trendChange', () => {
  it('says which way the latest value moved and by how much', () => {
    expect(trendChange(hb([10, 12.6]).points)).toEqual({ direction: 'up', delta: 2.6 });
    expect(trendChange(hb([12.6, 10]).points)).toEqual({ direction: 'down', delta: -2.6 });
  });

  it('calls a tiny move steady', () => {
    expect(trendChange(hb([13, 13.1]).points).direction).toBe('steady');
    expect(trendChange(hb([13, 13]).points).direction).toBe('steady');
  });

  it('needs two values', () => {
    expect(trendChange(hb([13]).points)).toBeNull();
  });

  it('copes with a previous value of zero', () => {
    expect(trendChange(hb([0, 2]).points).direction).toBe('up');
    expect(trendChange(hb([0, 0]).points).direction).toBe('steady');
  });
});

describe('latestFlag', () => {
  it('is the flag of the newest point', () => {
    const s = hb([10, 12.6]);
    s.points[1].flag = 'LOW';
    expect(latestFlag(s)).toBe('LOW');
  });
});

describe('referenceLines', () => {
  it('draws only the limits the parameter has', () => {
    expect(referenceLines(hb([1, 2])).map((l) => l.label)).toEqual(['Critical low', 'Low', 'High', 'Critical high']);
    expect(referenceLines({ refHigh: 200, points: [] }).map((l) => l.y)).toEqual([200]);
    expect(referenceLines({ points: [] })).toEqual([]);
  });
});

describe('yDomain', () => {
  it('spans the values and the normal range with a little padding', () => {
    const [low, high] = yDomain(hb([10, 12.6]));
    expect(low).toBeLessThan(10);
    expect(high).toBeGreaterThan(15.5);
  });

  it('stays sensible for a single repeated value and no range', () => {
    const [low, high] = yDomain({ points: [point(5, 1), point(5, 2)] });
    expect(low).toBeLessThan(5);
    expect(high).toBeGreaterThan(5);
  });
});

describe('groupByTest', () => {
  it('keeps the server order within each test', () => {
    const groups = groupByTest([
      { testCode: 'CBC', testName: 'CBC', name: 'Hb' },
      { testCode: 'LFT', testName: 'LFT', name: 'ALT' },
      { testCode: 'CBC', testName: 'CBC', name: 'WBC' },
    ]);
    expect(groups.map((g) => g.testCode)).toEqual(['CBC', 'LFT']);
    expect(groups[0].series.map((s) => s.name)).toEqual(['Hb', 'WBC']);
  });
});
