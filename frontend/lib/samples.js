'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import QRCode from 'qrcode';
import { api } from './api';

/** Data hooks and helpers for samples and the front desk's notifications (Docs/API.md "Samples"). */

export const sampleKeys = {
  all: ['samples'],
  waiting: (status, page) => ['samples', 'waiting', status, page],
  detail: (id) => ['samples', id],
  byCode: (code) => ['samples', 'code', code],
  forOrder: (orderId) => ['samples', 'order', orderId],
  mine: ['samples', 'mine'],
  notifications: ['notifications'],
};

// ------------------------------------------------------------------ pure helpers

/** Patient-facing stages, in order (matches the SampleJourney component). */
export const JOURNEY_STAGES = ['Ordered', 'Collected', 'At the lab', 'Testing', 'Verified', 'Report ready'];

/** Which journey stop a sample status belongs to. Internal steps map onto the nearest patient-facing one. */
const STAGE_OF = {
  ORDERED: 0,
  COLLECTED: 1,
  RECEIVED_AT_LAB: 2,
  IN_TESTING: 3,
  RESULT_ENTERED: 3,
  VERIFIED: 4,
  // The report exists as soon as it is verified, but the patient can't open it until it is dispatched.
  REPORT_GENERATED: 4,
  DISPATCHED: 5,
};

/**
 * Turns a sample's events into what the journey line needs: the current stop and, for each finished
 * stop, when it happened (an ISO string; the caller formats it). A rejected sample stays where it was.
 */
export function journeyFor(sample) {
  const times = Array(JOURNEY_STAGES.length).fill(null);
  let current = 0;
  for (const event of sample.events ?? []) {
    const stage = STAGE_OF[event.status];
    if (stage === undefined) continue; // REJECTED / CANCELLED aren't stops on the line
    current = Math.max(current, stage);
    if (times[stage] === null) times[stage] = event.occurredAt;
  }
  return { current, times: times.slice(0, current) };
}

/** A short, plain-language line for a sample's state, for patients and lists. */
export function plainStatus(status) {
  return {
    ORDERED: 'Waiting for your sample to be taken',
    COLLECTED: 'Sample taken — on its way to the lab',
    RECEIVED_AT_LAB: 'At the lab',
    IN_TESTING: 'Being tested',
    RESULT_ENTERED: 'Being checked',
    VERIFIED: 'Result checked',
    REPORT_GENERATED: 'Result checked — your report will be sent to you',
    DISPATCHED: 'Report ready',
    REJECTED: 'A new sample is needed',
    CANCELLED: 'Cancelled',
  }[status] ?? status;
}

/** Short status for staff lists and badges. */
export const SAMPLE_STATUS_LABEL = {
  ORDERED: 'To collect',
  COLLECTED: 'To receive',
  RECEIVED_AT_LAB: 'At the lab',
  IN_TESTING: 'In testing',
  RESULT_ENTERED: 'Result entered',
  VERIFIED: 'Verified',
  REPORT_GENERATED: 'To dispatch',
  DISPATCHED: 'Dispatched',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

/** Rejection reasons at the receipt check, in the order they are offered. */
export const RECEIPT_REJECT_REASONS = [
  { value: 'HEMOLYZED', label: 'Hemolyzed', hint: 'Red cells have burst — most results are unreliable' },
  { value: 'CLOTTED', label: 'Clotted', hint: 'The sample has coagulated' },
  { value: 'INSUFFICIENT_VOLUME', label: 'Not enough sample', hint: 'Too little to run the tests' },
  { value: 'OTHER', label: 'Something else', hint: 'Say what is wrong' },
];

export const REJECTION_REASON_LABEL = {
  HEMOLYZED: 'Hemolyzed',
  CLOTTED: 'Clotted',
  INSUFFICIENT_VOLUME: 'Not enough sample',
  SAMPLE_EXHAUSTED: 'Sample used up',
  SAMPLE_DEGRADED: 'Sample degraded',
  OTHER: 'Other',
};

export const BODY_SITES = ['Left arm', 'Right arm', 'Left hand', 'Right hand'];

/** Every tube or container a technician can pick, in the order they are shown. */
export const TUBE_CHOICES = ['EDTA', 'PLAIN', 'SST', 'CITRATE', 'FLUORIDE', 'HEPARIN', 'URINE_CUP', 'STOOL_CUP', 'SWAB_TUBE'];

const CUPS = new Set(['URINE_CUP', 'STOOL_CUP', 'SWAB_TUBE']);
export const isBloodTube = (tube) => !CUPS.has(tube);

/**
 * Validates the collection form before it is sent. Blood needs a body site; a wrong tube needs the
 * technician to confirm. Returns { site, mismatch } errors as messages or null.
 */
export function collectionProblems({ tube, site, required, confirmed }) {
  return {
    site: isBloodTube(tube) && !site?.trim() ? 'Say where the blood was drawn from' : null,
    mismatch: tube !== required && !confirmed ? 'mismatch' : null,
  };
}

/** The scan field takes a typed code or a barcode scanner's keystrokes; both end up as a code like LAB-20260929-0007. */
export function normalizeSampleCode(input) {
  const code = String(input ?? '').trim().toUpperCase().replace(/\s+/g, '');
  return /^LAB-\d{8}-\d{4,}$/.test(code) ? code : null;
}

/**
 * The QR code for a sample code as rows of booleans (true = dark module). Rendered as SVG by the
 * label; nothing is injected as HTML.
 */
export function qrRows(text) {
  const { size, data } = QRCode.create(text, { errorCorrectionLevel: 'M' }).modules;
  return Array.from({ length: size }, (_, y) => Array.from({ length: size }, (_, x) => Boolean(data[y * size + x])));
}

// ------------------------------------------------------------------ lab lists and lookup

/** Samples waiting at one step: ORDERED (to collect) or COLLECTED (to receive); refreshed every 20 s. */
export function useWaitingSamples(status, page = 0, size = 20, enabled = true) {
  return useQuery({
    queryKey: sampleKeys.waiting(status, page),
    queryFn: ({ signal }) => api(`/samples?${new URLSearchParams({ status, page: String(page), size: String(size) })}`, { signal }),
    placeholderData: keepPreviousData,
    refetchInterval: 20_000,
    enabled,
  });
}

export function useSample(id) {
  return useQuery({
    queryKey: sampleKeys.detail(id),
    queryFn: ({ signal }) => api(`/samples/${id}/status`, { signal }),
    retry: false,
  });
}

/** Look a sample up by its label code (a plain async call — used by the scan field). */
export function findSampleByCode(code) {
  return api(`/samples/by-code/${encodeURIComponent(code)}`);
}

export function useOrderSamples(orderId) {
  return useQuery({
    queryKey: sampleKeys.forOrder(orderId),
    queryFn: ({ signal }) => api(`/lab-orders/${orderId}/samples`, { signal }),
    enabled: Boolean(orderId),
    retry: false,
  });
}

export function useMySamples() {
  return useQuery({
    queryKey: sampleKeys.mine,
    queryFn: ({ signal }) => api('/samples/mine', { signal }),
    refetchInterval: 60_000,
  });
}

// ------------------------------------------------------------------ actions

function afterChange(queryClient, sample) {
  queryClient.setQueryData(sampleKeys.detail(sample.id), sample);
  queryClient.invalidateQueries({ queryKey: sampleKeys.all });
  queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  queryClient.invalidateQueries({ queryKey: sampleKeys.notifications });
}

export function useCollectSample(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(`/samples/${id}/collect`, { method: 'POST', body }),
    onSuccess: (sample) => afterChange(queryClient, sample),
  });
}

/** The receipt check: `{ accepted: true }` or `{ accepted: false, reason, note }`. */
export function useReceiveSample(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(`/samples/${id}/receive`, { method: 'POST', body }),
    onSuccess: (sample) => afterChange(queryClient, sample),
  });
}

// ------------------------------------------------------------------ front-desk notifications

/** The front desk's open items; refreshed every 30 s so a rejection shows up without a reload. */
export function useNotifications(enabled = true) {
  return useQuery({
    queryKey: sampleKeys.notifications,
    queryFn: ({ signal }) => api('/notifications', { signal }),
    refetchInterval: 30_000,
    enabled,
  });
}

export function useHandleNotification() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => api(`/notifications/${id}/handle`, { method: 'POST', body: {} }),
    onSuccess: (list) => {
      queryClient.setQueryData(sampleKeys.notifications, list);
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
