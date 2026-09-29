'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';
import { sampleKeys } from './samples';

/** Data hooks and helpers for testing, results, verification and reports (Docs/API.md "Samples" and "Reports"). */

export const resultKeys = {
  toTest: (page) => ['results', 'to-test', page],
  detail: (sampleId) => ['results', sampleId],
  queue: (page) => ['results', 'queue', page],
  verifiedByMe: (page) => ['results', 'verified-by-me', page],
  reports: ['reports'],
  report: (sampleId) => ['reports', sampleId],
  myReports: ['reports', 'mine'],
  orderedByMe: (page) => ['reports', 'ordered-by-me', page],
  toDispatch: (page) => ['reports', 'to-dispatch', page],
};

// ------------------------------------------------------------------ pure helpers

const FLAG_LABEL = { NORMAL: 'Normal', LOW: 'Low', HIGH: 'High', CRITICAL_LOW: 'Critical low', CRITICAL_HIGH: 'Critical high' };
export const flagLabel = (flag) => FLAG_LABEL[flag] ?? '';

/** The app's status-badge vocabulary for a flag (Design.md §4). */
export function flagStatus(flag) {
  if (flag === 'CRITICAL_LOW' || flag === 'CRITICAL_HIGH') return 'critical';
  if (flag === 'LOW') return 'low';
  if (flag === 'HIGH') return 'high';
  return 'normal';
}

export const isCritical = (flag) => flag === 'CRITICAL_LOW' || flag === 'CRITICAL_HIGH';
export const isAbnormal = (flag) => Boolean(flag) && flag !== 'NORMAL';

const num = (v) => (v === null || v === undefined ? null : Number(v));

/**
 * The same range check the server runs, so the technician sees the flag as they type. The server is
 * still the authority: it recomputes and stores its own flag.
 */
export function flagFor(value, spec) {
  const v = Number(value);
  if (value === '' || value === null || !Number.isFinite(v)) return null;
  const [refLow, refHigh, critLow, critHigh] = [num(spec.refLow), num(spec.refHigh), num(spec.criticalLow), num(spec.criticalHigh)];
  if (critLow !== null && v <= critLow) return 'CRITICAL_LOW';
  if (critHigh !== null && v >= critHigh) return 'CRITICAL_HIGH';
  if (refLow !== null && v < refLow) return 'LOW';
  if (refHigh !== null && v > refHigh) return 'HIGH';
  return 'NORMAL';
}

/** "12 – 15.5", "< 200", "> 40" or "—" for a parameter or a stored value. */
export function formatRange(spec) {
  const low = num(spec.refLow);
  const high = num(spec.refHigh);
  if (low !== null && high !== null) return `${low} – ${high}`;
  if (high !== null) return `< ${high}`;
  if (low !== null) return `> ${low}`;
  return '—';
}

/** A stored value as text: a number without trailing zeros, or the text as entered. */
export function formatValue(v) {
  if (v.valueType === 'NUMERIC' && v.numericValue !== null && v.numericValue !== undefined) return String(Number(v.numericValue));
  return v.textValue ?? '';
}

/**
 * Checks the entry form against the sheet before it is sent: every parameter needs a value, and
 * numbers must be numbers with at most four decimals. Returns { [parameterId]: message }.
 */
export function entryProblems(sheet, values) {
  const problems = {};
  for (const test of sheet) {
    for (const p of test.parameters) {
      const raw = String(values[p.parameterId] ?? '').trim();
      if (raw === '') {
        problems[p.parameterId] = 'Enter a value';
      } else if (p.valueType === 'NUMERIC') {
        if (!Number.isFinite(Number(raw))) problems[p.parameterId] = 'Must be a number';
        else if ((raw.split('.')[1] ?? '').length > 4) problems[p.parameterId] = 'At most 4 decimals';
      }
    }
  }
  return problems;
}

/** The request body for entering results: every parameter, in sheet order. */
export function entryBody(sheet, values, analyzer) {
  return {
    analyzer: analyzer?.trim() || null,
    values: sheet.flatMap((t) => t.parameters.map((p) => ({ parameterId: p.parameterId, value: String(values[p.parameterId]).trim() }))),
  };
}

/** How many entered numbers are outside their ranges, and whether any is critical — for the live summary. */
export function entrySummary(sheet, values) {
  let abnormal = 0;
  let critical = 0;
  for (const test of sheet) {
    for (const p of test.parameters) {
      if (p.valueType !== 'NUMERIC') continue;
      const flag = flagFor(values[p.parameterId], p);
      if (isAbnormal(flag)) abnormal += 1;
      if (isCritical(flag)) critical += 1;
    }
  }
  return { abnormal, critical };
}

export const RETURN_REASONS = [
  { value: 'IMPLAUSIBLE_VALUE', label: 'Implausible value', hint: 'The number doesn’t make sense for this patient' },
  { value: 'INCONSISTENT_WITH_HISTORY', label: 'Inconsistent with history', hint: 'Far from their earlier results' },
  { value: 'CRITICAL_VALUE_CONFIRMATION', label: 'Critical value needs confirming', hint: 'Rerun before signing off' },
  { value: 'QC_CONCERN', label: 'Quality-control concern', hint: 'The run or analyzer is in doubt' },
  { value: 'OTHER', label: 'Something else', hint: 'Say why' },
];
export const RETURN_REASON_LABEL = Object.fromEntries(RETURN_REASONS.map((r) => [r.value, r.label]));

/** Reasons a technician can give for rejecting a sample that is in testing. */
export const TESTING_REJECT_REASONS = [
  { value: 'SAMPLE_EXHAUSTED', label: 'Sample used up', hint: 'Not enough left to run or rerun the test' },
  { value: 'SAMPLE_DEGRADED', label: 'Sample degraded', hint: 'Too old or unstable to give a reliable result' },
  { value: 'OTHER', label: 'Something else', hint: 'Say what is wrong' },
];

export const DISPATCH_CHANNELS = [
  { value: 'EMAIL', label: 'Email', hint: 'A notice to the patient’s account email — the report stays behind sign-in' },
  { value: 'DOWNLOAD_LINK', label: 'Download link', hint: 'Available to open and download in the patient’s account' },
  { value: 'SMS', label: 'SMS', hint: 'Not set up yet', disabled: true },
];
export const CHANNEL_LABEL = { EMAIL: 'Emailed', DOWNLOAD_LINK: 'Download link', SMS: 'SMS' };

/** Keeps a trend as plain numbers, oldest first, ending with today's value — for a sparkline. */
export function trendSeries(points, current) {
  const earlier = [...(points ?? [])].reverse().map((p) => Number(p.value));
  return [...earlier, Number(current)];
}

// ------------------------------------------------------------------ bench

export function useToTest(page = 0, size = 20) {
  return useQuery({
    queryKey: resultKeys.toTest(page),
    queryFn: ({ signal }) => api(`/samples/to-test?${new URLSearchParams({ page: String(page), size: String(size) })}`, { signal }),
    placeholderData: keepPreviousData,
    refetchInterval: 20_000,
  });
}

/** The entry sheet, every attempt so far and (for a pathologist) the patient's earlier values. */
export function useSampleResults(sampleId) {
  return useQuery({
    queryKey: resultKeys.detail(sampleId),
    queryFn: ({ signal }) => api(`/samples/${sampleId}/results`, { signal }),
    enabled: Boolean(sampleId),
    retry: false,
    refetchOnWindowFocus: false,
  });
}

function afterChange(queryClient, results) {
  queryClient.setQueryData(resultKeys.detail(results.sampleId), results);
  queryClient.invalidateQueries({ queryKey: ['results'] });
  queryClient.invalidateQueries({ queryKey: sampleKeys.all });
  queryClient.invalidateQueries({ queryKey: resultKeys.reports });
  queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  queryClient.invalidateQueries({ queryKey: sampleKeys.notifications });
}

function useSampleAction(sampleId, path, method = 'POST') {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(`/samples/${sampleId}/${path}`, { method, body: body ?? {} }),
    onSuccess: (results) => afterChange(queryClient, results),
  });
}

export const useStartTesting = (sampleId) => useSampleAction(sampleId, 'start-testing');
export const useEnterResults = (sampleId) => useSampleAction(sampleId, 'results');
export const useRejectInTesting = (sampleId) => useSampleAction(sampleId, 'reject');
export const useVerify = (sampleId) => useSampleAction(sampleId, 'verify');
export const useReturnForRetest = (sampleId) => useSampleAction(sampleId, 'return-for-retest');

// ------------------------------------------------------------------ pathologist

/** The verification queue: critical first, then out-of-range, then the longest waiting. */
export function usePendingVerification(page = 0, size = 50) {
  return useQuery({
    queryKey: resultKeys.queue(page),
    queryFn: ({ signal }) => api(`/samples/pending-verification?${new URLSearchParams({ page: String(page), size: String(size) })}`, { signal }),
    placeholderData: keepPreviousData,
    refetchInterval: 20_000,
  });
}

export function useVerifiedByMe(page = 0, size = 20) {
  return useQuery({
    queryKey: resultKeys.verifiedByMe(page),
    queryFn: ({ signal }) => api(`/samples/verified-by-me?${new URLSearchParams({ page: String(page), size: String(size) })}`, { signal }),
    placeholderData: keepPreviousData,
  });
}

// ------------------------------------------------------------------ reports

export function reportPdfUrl(sampleId, download = false) {
  return `/api/reports/${sampleId}/pdf${download ? '?download=true' : ''}`;
}

export function useReport(sampleId) {
  return useQuery({
    queryKey: resultKeys.report(sampleId),
    queryFn: ({ signal }) => api(`/reports/${sampleId}`, { signal }),
    retry: false,
    refetchOnWindowFocus: false,
  });
}

export function useMyReports() {
  return useQuery({ queryKey: resultKeys.myReports, queryFn: ({ signal }) => api('/reports/mine', { signal }) });
}

export function useOrderedReports(page = 0, size = 20) {
  return useQuery({
    queryKey: resultKeys.orderedByMe(page),
    queryFn: ({ signal }) => api(`/reports/ordered-by-me?${new URLSearchParams({ page: String(page), size: String(size) })}`, { signal }),
    placeholderData: keepPreviousData,
  });
}

export function useReportsToDispatch(page = 0, size = 20) {
  return useQuery({
    queryKey: resultKeys.toDispatch(page),
    queryFn: ({ signal }) => api(`/reports/to-dispatch?${new URLSearchParams({ page: String(page), size: String(size) })}`, { signal }),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });
}

export function useDispatchReport(sampleId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (channel) => api(`/reports/${sampleId}/dispatch`, { method: 'POST', body: { channel } }),
    onSuccess: (report) => {
      queryClient.setQueryData(resultKeys.report(sampleId), report);
      queryClient.invalidateQueries({ queryKey: resultKeys.reports });
      queryClient.invalidateQueries({ queryKey: sampleKeys.all });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
