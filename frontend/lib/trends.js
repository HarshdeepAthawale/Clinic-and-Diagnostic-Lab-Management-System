'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from './api';

/** A patient's numbers across visits (Docs/API.md, ADR-029). */

export function useTrends(patientId) {
  return useQuery({
    queryKey: ['trends', patientId],
    queryFn: ({ signal }) => api(`/patients/${patientId}/trends`, { signal }),
    enabled: Boolean(patientId),
    retry: false,
    refetchOnWindowFocus: false,
  });
}

/** Chart points for one series: an ISO day and the number. */
export function chartRows(series) {
  return series.points.map((p) => ({ date: p.verifiedAt.slice(0, 10), value: Number(p.value), sampleCode: p.sampleCode }));
}

/**
 * How the latest value compares with the one before it. `steady` when it moved by less than 2% of the earlier
 * value, so a wobble of the analyzer isn't reported as a trend.
 */
export function trendChange(points) {
  if (points.length < 2) return null;
  const last = Number(points[points.length - 1].value);
  const before = Number(points[points.length - 2].value);
  const delta = last - before;
  const steady = before !== 0 ? Math.abs(delta) / Math.abs(before) < 0.02 : delta === 0;
  return { direction: steady ? 'steady' : delta > 0 ? 'up' : 'down', delta: Math.round(delta * 1000) / 1000 };
}

/** Whether the latest value is in range, from the flag the server stored with it. */
export function latestFlag(series) {
  return series.points[series.points.length - 1].flag ?? null;
}

/** Normal-range and critical-limit lines for the chart, only for the limits the parameter has. */
export function referenceLines(series) {
  const lines = [];
  if (series.criticalLow != null) lines.push({ y: Number(series.criticalLow), label: 'Critical low', color: 'var(--critical)' });
  if (series.refLow != null) lines.push({ y: Number(series.refLow), label: 'Low', color: 'var(--border-strong)' });
  if (series.refHigh != null) lines.push({ y: Number(series.refHigh), label: 'High', color: 'var(--border-strong)' });
  if (series.criticalHigh != null) lines.push({ y: Number(series.criticalHigh), label: 'Critical high', color: 'var(--critical)' });
  return lines;
}

/**
 * The y-axis range: the values plus the normal range (so the band is visible), with a little padding. Critical
 * limits are left out when they are far away, otherwise a stable value looks like a flat line.
 */
export function yDomain(series) {
  const values = series.points.map((p) => Number(p.value));
  const bounds = [...values];
  if (series.refLow != null) bounds.push(Number(series.refLow));
  if (series.refHigh != null) bounds.push(Number(series.refHigh));
  const low = Math.min(...bounds);
  const high = Math.max(...bounds);
  const pad = (high - low || Math.abs(high) || 1) * 0.15;
  return [Math.floor((low - pad) * 10) / 10, Math.ceil((high + pad) * 10) / 10];
}

/** Trend cards grouped by test, in the server's order: [{ testCode, testName, series: [...] }]. */
export function groupByTest(seriesList) {
  const groups = new Map();
  for (const s of seriesList) {
    if (!groups.has(s.testCode)) groups.set(s.testCode, { testCode: s.testCode, testName: s.testName, series: [] });
    groups.get(s.testCode).series.push(s);
  }
  return [...groups.values()];
}
