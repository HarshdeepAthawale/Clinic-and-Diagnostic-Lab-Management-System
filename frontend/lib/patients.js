'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';

/** Data hooks for patient records (Docs/API.md "Patients"). */

export const patientKeys = {
  all: ['patients'],
  search: (q, page) => ['patients', 'search', q, page],
  details: (id) => ['patients', id, 'details'],
  record: (id) => ['patients', id, 'record'],
  me: ['patients', 'me'],
  accessLog: (filters) => ['access-log', filters],
};

export function usePatientSearch(q, page = 0, size = 20) {
  return useQuery({
    queryKey: patientKeys.search(q, page),
    queryFn: ({ signal }) =>
      api(`/patients?${new URLSearchParams({ q, page: String(page), size: String(size) })}`, { signal }),
    placeholderData: keepPreviousData,
  });
}

export function usePatientDetails(id) {
  return useQuery({ queryKey: patientKeys.details(id), queryFn: ({ signal }) => api(`/patients/${id}`, { signal }) });
}

/** Full record. For doctors this is logged server-side on every fetch, so don't refetch casually. */
export function usePatientRecord(id) {
  return useQuery({
    queryKey: patientKeys.record(id),
    queryFn: ({ signal }) => api(`/patients/${id}/history`, { signal }),
    retry: false,
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
  });
}

export function useMyRecord() {
  return useQuery({ queryKey: patientKeys.me, queryFn: ({ signal }) => api('/patients/me', { signal }) });
}

export function useRegisterPatient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api('/patients', { method: 'POST', body }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: patientKeys.all });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useUpdateDemographics(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(`/patients/${id}`, { method: 'PATCH', body }),
    onSuccess: (details) => {
      queryClient.setQueryData(patientKeys.details(id), details);
      queryClient.invalidateQueries({ queryKey: ['patients', 'search'] });
    },
  });
}

export function useReissueRegistrationCode(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api(`/patients/${id}/registration-code`, { method: 'POST' }),
    onSuccess: (result) => queryClient.setQueryData(patientKeys.details(id), result.patient),
  });
}

export function useUpdateClinical(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(`/patients/${id}/clinical`, { method: 'PATCH', body }),
    onSuccess: (record) => queryClient.setQueryData(patientKeys.record(id), record),
  });
}

export function useAccessLog(filters, page = 0, size = 25) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  Object.entries(filters).forEach(([key, value]) => value && params.set(key, value));
  return useQuery({
    queryKey: patientKeys.accessLog({ ...filters, page }),
    queryFn: ({ signal }) => api(`/admin/access-log?${params}`, { signal }),
    placeholderData: keepPreviousData,
  });
}
