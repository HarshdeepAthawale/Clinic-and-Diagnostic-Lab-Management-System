'use client';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from './api';

/** Data hook and helpers for the admin insights (Docs/API.md "Admin", ADR-026). */

export const RANGES = [
  { value: '7', label: '7 days' },
  { value: '30', label: '30 days' },
  { value: '90', label: '90 days' },
];

export function useInsights(days) {
  return useQuery({
    queryKey: ['insights', days],
    queryFn: ({ signal }) => api(`/admin/dashboard?days=${days}`, { signal }),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}

/** Whole-number percent change against the previous period; null when there is nothing to compare with. */
export function pctChange(current, previous) {
  const now = Number(current);
  const before = Number(previous);
  if (!Number.isFinite(now) || !Number.isFinite(before) || before === 0) return null;
  return Math.round(((now - before) / before) * 100);
}

/** "45 min", "2 h 5 min", "1 d 3 h": turnaround at the precision people read it. */
export function formatDuration(minutes) {
  if (minutes === null || minutes === undefined) return '—';
  const m = Math.max(0, Math.round(minutes));
  if (m < 60) return `${m} min`;
  if (m < 24 * 60) {
    const h = Math.floor(m / 60);
    const rest = m % 60;
    return rest ? `${h} h ${rest} min` : `${h} h`;
  }
  const d = Math.floor(m / (24 * 60));
  const h = Math.round((m % (24 * 60)) / 60);
  return h ? `${d} d ${h} h` : `${d} d`;
}

/** "29 Sep" from an ISO date (yyyy-mm-dd), without time-zone drift. */
export function shortDay(isoDate) {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' });
}

/** Number series for a sparkline from the daily points. */
export function trend(series, key) {
  return series.map((p) => Number(p[key] ?? 0));
}

export function sum(series, key) {
  return series.reduce((total, p) => total + Number(p[key] ?? 0), 0);
}

/**
 * The turnaround heatmap: one row per test (busiest first, at most `limit`), one column per day, each cell the
 * median minutes that day or null when nothing was reported. `max` scales the colour.
 */
export function heatGrid(tat, cells, days, limit = 8) {
  const rows = tat.slice(0, limit);
  const byKey = new Map(cells.map((c) => [`${c.testId}|${c.date}`, c]));
  const grid = rows.map((t) => ({
    test: t,
    cells: days.map((date) => {
      const c = byKey.get(`${t.testId}|${date}`);
      return c ? { date, median: c.medianMinutes, samples: c.samples } : { date, median: null, samples: 0 };
    }),
  }));
  const max = Math.max(0, ...grid.flatMap((r) => r.cells.map((c) => c.median ?? 0)));
  return { rows: grid, max };
}

/** 0–1 heat for a cell against the grid's slowest; null cells have none. */
export function heatLevel(median, max) {
  if (median === null || median === undefined || !max) return null;
  return Math.min(1, median / max);
}

/** The days of the period as ISO dates, oldest first, taken from the server's series. */
export function periodDays(series) {
  return series.map((p) => p.date);
}

/** Where the time goes for a test: the three stages as shares of their sum, for the stage bar. */
export function stageShares(test) {
  const stages = [
    ['To the lab', test.toLabMinutes],
    ['Testing', test.testingMinutes],
    ['Verification', test.verificationMinutes],
  ].filter(([, v]) => v !== null && v !== undefined);
  const total = stages.reduce((s, [, v]) => s + v, 0);
  return stages.map(([label, minutes]) => ({ label, minutes, share: total ? minutes / total : 0 }));
}
