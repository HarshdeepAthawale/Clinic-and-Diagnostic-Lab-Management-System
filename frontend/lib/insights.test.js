import { describe, expect, it } from 'vitest';
import { formatDuration, heatGrid, heatLevel, pctChange, shortDay, stageShares, sum, trend } from './insights';

describe('pctChange', () => {
  it('rounds the change against the previous period', () => {
    expect(pctChange(150, 100)).toBe(50);
    expect(pctChange(80, 100)).toBe(-20);
    expect(pctChange(100, 100)).toBe(0);
    expect(pctChange('1250.50', '1000')).toBe(25);
  });

  it('has no answer when there is nothing to compare with', () => {
    expect(pctChange(10, 0)).toBeNull();
    expect(pctChange(0, 0)).toBeNull();
    expect(pctChange(undefined, 5)).toBeNull();
  });
});

describe('formatDuration', () => {
  it('reads at the right precision', () => {
    expect(formatDuration(0)).toBe('0 min');
    expect(formatDuration(45)).toBe('45 min');
    expect(formatDuration(60)).toBe('1 h');
    expect(formatDuration(125)).toBe('2 h 5 min');
    expect(formatDuration(24 * 60)).toBe('1 d');
    expect(formatDuration(27 * 60)).toBe('1 d 3 h');
  });

  it('shows a dash when there is no figure', () => {
    expect(formatDuration(null)).toBe('—');
    expect(formatDuration(undefined)).toBe('—');
  });
});

describe('dates and series', () => {
  it('formats an ISO date without shifting the day', () => {
    expect(shortDay('2026-09-29')).toBe('29 Sept');
    expect(shortDay('2026-01-01')).toBe('1 Jan');
  });

  it('pulls a number series and totals from the daily points', () => {
    const series = [{ revenue: '100.5', patients: 2 }, { revenue: '0', patients: 0 }, { revenue: 50, patients: 3 }];
    expect(trend(series, 'patients')).toEqual([2, 0, 3]);
    expect(sum(series, 'revenue')).toBe(150.5);
  });
});

describe('heatGrid', () => {
  const tat = [{ testId: 'a', code: 'CBC' }, { testId: 'b', code: 'LFT' }, { testId: 'c', code: 'KFT' }];
  const cells = [
    { testId: 'a', date: '2026-09-28', medianMinutes: 100, samples: 2 },
    { testId: 'b', date: '2026-09-29', medianMinutes: 400, samples: 1 },
  ];
  const days = ['2026-09-28', '2026-09-29'];

  it('lays tests against days, with gaps where nothing was reported', () => {
    const { rows, max } = heatGrid(tat, cells, days, 2);
    expect(rows.map((r) => r.test.code)).toEqual(['CBC', 'LFT']);
    expect(rows[0].cells).toEqual([
      { date: '2026-09-28', median: 100, samples: 2 },
      { date: '2026-09-29', median: null, samples: 0 },
    ]);
    expect(max).toBe(400);
  });

  it('scales heat against the slowest cell and leaves gaps cold', () => {
    expect(heatLevel(200, 400)).toBe(0.5);
    expect(heatLevel(400, 400)).toBe(1);
    expect(heatLevel(null, 400)).toBeNull();
    expect(heatLevel(50, 0)).toBeNull();
  });
});

describe('stageShares', () => {
  it('splits the time across the stages that were measured', () => {
    const shares = stageShares({ toLabMinutes: 20, testingMinutes: 60, verificationMinutes: 20 });
    expect(shares.map((s) => s.label)).toEqual(['To the lab', 'Testing', 'Verification']);
    expect(shares.map((s) => s.share)).toEqual([0.2, 0.6, 0.2]);
  });

  it('skips stages with no figure', () => {
    expect(stageShares({ toLabMinutes: null, testingMinutes: 30, verificationMinutes: 30 }).map((s) => s.label)).toEqual(['Testing', 'Verification']);
    expect(stageShares({})).toEqual([]);
  });
});
