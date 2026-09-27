'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';

/** Data hooks for the lab test catalog and lab orders (Docs/API.md "Lab Tests & Orders"). */

export const labKeys = {
  tests: (q, includeInactive) => ['lab-tests', q, includeInactive],
  test: (id) => ['lab-tests', 'detail', id],
  orders: ['lab-orders'],
  order: (id) => ['lab-orders', id],
  queue: (page) => ['lab-orders', 'queue', page],
  mine: ['lab-orders', 'mine'],
  forConsultation: (consultationId) => ['lab-orders', 'consultation', consultationId],
  forPatient: (patientId) => ['patients', patientId, 'lab-orders'],
};

// ------------------------------------------------------------------ pure helpers

/** Distinct tubes across tests with how many tests share each, most-used first: [{ tube, count }]. */
export function tubeSummary(tests) {
  const counts = new Map();
  for (const t of tests) {
    const tube = t.tubeType ?? t.requiredTubeType;
    counts.set(tube, (counts.get(tube) ?? 0) + 1);
  }
  return [...counts].map(([tube, count]) => ({ tube, count })).sort((a, b) => b.count - a.count || a.tube.localeCompare(b.tube));
}

/** Tests that need the patient to do something beforehand, e.g. fasting. */
export function prepItems(tests) {
  return tests.filter((t) => t.prepInstructions).map((t) => ({ name: t.name, prep: t.prepInstructions }));
}

export function orderTotal(tests) {
  return tests.reduce((sum, t) => sum + Number(t.price ?? 0), 0);
}

/** Longest turnaround across the tests, in hours — when the last result can be expected. */
export function slowestTurnaround(tests) {
  return tests.reduce((max, t) => Math.max(max, t.turnaroundHours ?? 0), 0);
}

/** "4 h", "2 days" */
export function formatTurnaround(hours) {
  if (hours < 24) return `${hours} h`;
  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? '' : 's'}`;
}

/** Catalog rows grouped by category, keeping the server's order within each group. */
export function groupByCategory(tests) {
  const groups = new Map();
  for (const t of tests) {
    if (!groups.has(t.category)) groups.set(t.category, []);
    groups.get(t.category).push(t);
  }
  return [...groups].map(([category, items]) => ({ category, items }));
}

// ------------------------------------------------------------------ catalog

export function useLabTests(q = '', { includeInactive = false, enabled = true } = {}) {
  return useQuery({
    queryKey: labKeys.tests(q, includeInactive),
    queryFn: ({ signal }) =>
      api(`/lab-tests?${new URLSearchParams({ q, includeInactive: String(includeInactive) })}`, { signal }),
    staleTime: 5 * 60_000,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useLabTest(id) {
  return useQuery({
    queryKey: labKeys.test(id),
    queryFn: ({ signal }) => api(`/lab-tests/${id}`, { signal }),
    enabled: Boolean(id),
    retry: false,
  });
}

export function useSaveLabTest(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(id ? `/lab-tests/${id}` : '/lab-tests', { method: id ? 'PUT' : 'POST', body }),
    onSuccess: (test) => {
      queryClient.setQueryData(labKeys.test(test.id), test);
      queryClient.invalidateQueries({ queryKey: ['lab-tests'] });
    },
  });
}

// ------------------------------------------------------------------ orders

function invalidateOrders(queryClient, order) {
  queryClient.invalidateQueries({ queryKey: labKeys.orders });
  queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  if (order?.patient) queryClient.invalidateQueries({ queryKey: labKeys.forPatient(order.patient.id) });
  if (order?.consultationId) {
    queryClient.setQueryData(labKeys.forConsultation(order.consultationId), order.status === 'ORDERED' ? order : null);
  }
}

/** Order tests — from a consultation ({ consultationId }) or directly ({ patientId }). */
export function useOrderTests() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api('/lab-orders', { method: 'POST', body }),
    onSuccess: (order) => invalidateOrders(queryClient, order),
  });
}

export function useRemoveOrderItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, itemId }) => api(`/lab-orders/${orderId}/items/${itemId}`, { method: 'DELETE' }),
    onSuccess: (order) => invalidateOrders(queryClient, order),
  });
}

export function useCancelOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, reason }) => api(`/lab-orders/${orderId}/cancel`, { method: 'POST', body: { reason } }),
    onSuccess: (order) => invalidateOrders(queryClient, order),
  });
}

/** The open order in a consultation, or null (the API answers 204 when nothing is ordered yet). */
export function useConsultationLabOrder(consultationId) {
  return useQuery({
    queryKey: labKeys.forConsultation(consultationId),
    queryFn: ({ signal }) => api(`/consultations/${consultationId}/lab-order`, { signal }),
    enabled: Boolean(consultationId),
  });
}

export function useLabOrder(id) {
  return useQuery({
    queryKey: labKeys.order(id),
    queryFn: ({ signal }) => api(`/lab-orders/${id}`, { signal }),
    retry: false,
  });
}

export function useMyLabOrders() {
  return useQuery({
    queryKey: labKeys.mine,
    queryFn: ({ signal }) => api('/lab-orders/mine', { signal }),
  });
}

export function usePatientLabOrders(patientId, enabled = true) {
  return useQuery({
    queryKey: labKeys.forPatient(patientId),
    queryFn: ({ signal }) => api(`/patients/${patientId}/lab-orders`, { signal }),
    enabled: Boolean(patientId) && enabled,
    retry: false,
  });
}

/** The lab's incoming queue; refreshed every 30 s so new orders appear without a reload. */
export function useLabQueue(page = 0, size = 20) {
  return useQuery({
    queryKey: labKeys.queue(page),
    queryFn: ({ signal }) => api(`/lab-orders?${new URLSearchParams({ page: String(page), size: String(size) })}`, { signal }),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });
}
