'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';

/** Data hooks for consultations and prescriptions (Docs/API.md "Consultations & Prescriptions"). */

export const consultationKeys = {
  all: ['consultations'],
  forAppointment: (appointmentId) => ['consultations', 'appointment', appointmentId],
  detail: (id) => ['consultations', id],
  mine: (page) => ['consultations', 'mine', page],
  forPatient: (patientId) => ['consultations', 'patient', patientId],
  myPrescriptions: ['prescriptions', 'mine'],
  prescription: (id) => ['prescriptions', id],
  medicines: (q) => ['medicines', q],
};

/** Where a prescription's PDF is served; same access check as viewing it. */
export const prescriptionPdfUrl = (id) => `/api/prescriptions/${id}/pdf`;

/**
 * Opens (or re-opens) the doctor's consultation for an appointment. The endpoint is idempotent, so
 * it is safe to run as a query on page load.
 */
export function useConsultationForAppointment(appointmentId) {
  return useQuery({
    queryKey: consultationKeys.forAppointment(appointmentId),
    queryFn: () => api(`/appointments/${appointmentId}/consultation`, { method: 'POST' }),
    retry: false,
    refetchOnWindowFocus: false,
    staleTime: Infinity,
  });
}

export function useConsultation(id) {
  return useQuery({
    queryKey: consultationKeys.detail(id),
    queryFn: ({ signal }) => api(`/consultations/${id}`, { signal }),
    enabled: Boolean(id),
    retry: false,
  });
}

export function useMyConsultations(page = 0, size = 20) {
  return useQuery({
    queryKey: consultationKeys.mine(page),
    queryFn: ({ signal }) => api(`/consultations/mine?page=${page}&size=${size}`, { signal }),
    placeholderData: keepPreviousData,
  });
}

/** A patient's visits. For doctors this read is written to the access log, so don't refetch casually. */
export function usePatientConsultations(patientId) {
  return useQuery({
    queryKey: consultationKeys.forPatient(patientId),
    queryFn: ({ signal }) => api(`/patients/${patientId}/consultations`, { signal }),
    enabled: Boolean(patientId),
    retry: false,
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
  });
}

export function useMyPrescriptions() {
  return useQuery({ queryKey: consultationKeys.myPrescriptions, queryFn: ({ signal }) => api('/prescriptions/mine', { signal }) });
}

export function useMedicineSuggestions(q) {
  const query = q.trim();
  return useQuery({
    queryKey: consultationKeys.medicines(query.toLowerCase()),
    queryFn: ({ signal }) => api(`/medicines?q=${encodeURIComponent(query)}`, { signal }),
    enabled: query.length >= 2,
    staleTime: 60_000,
    placeholderData: keepPreviousData,
  });
}

/** Writes the saved consultation back into every cache that shows it. */
function useStoreConsultation(appointmentId) {
  const queryClient = useQueryClient();
  return (consultation) => {
    queryClient.setQueryData(consultationKeys.detail(consultation.id), consultation);
    if (appointmentId) queryClient.setQueryData(consultationKeys.forAppointment(appointmentId), consultation);
  };
}

export function useUpdateConsultation(id, appointmentId) {
  const store = useStoreConsultation(appointmentId);
  return useMutation({
    mutationFn: (body) => api(`/consultations/${id}`, { method: 'PATCH', body }),
    onSuccess: store,
  });
}

export function useCompleteConsultation(id, appointmentId) {
  const store = useStoreConsultation(appointmentId);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api(`/consultations/${id}/complete`, { method: 'POST' }),
    onSuccess: (consultation) => {
      store(consultation);
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      queryClient.invalidateQueries({ queryKey: ['queue'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['consultations', 'mine'] });
    },
  });
}

/** Issue the first prescription of a visit, or revise one (`reviseId`). Refreshes the consultation. */
export function useIssuePrescription(consultationId, appointmentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reviseId, ...body }) =>
      reviseId
        ? api(`/prescriptions/${reviseId}/revise`, { method: 'POST', body })
        : api(`/consultations/${consultationId}/prescriptions`, { method: 'POST', body }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: consultationKeys.detail(consultationId) });
      if (appointmentId) queryClient.invalidateQueries({ queryKey: consultationKeys.forAppointment(appointmentId) });
      queryClient.invalidateQueries({ queryKey: ['medicines'] });
    },
  });
}

/** Common frequency shorthands (morning-afternoon-night) offered in the prescription builder. */
export const FREQUENCIES = ['1-0-1', '1-1-1', '1-0-0', '0-0-1', '0-1-0', '1-1-1-1', 'SOS', 'Once a week'];

export const INSTRUCTIONS = ['After food', 'Before food', 'With food', 'At bedtime', 'Empty stomach', 'Apply locally'];

/** "1-0-1" → "Morning, night". Anything else is shown as written. */
export function describeFrequency(frequency) {
  const parts = frequency?.split('-');
  if (!parts || parts.length !== 3 || !parts.every((p) => /^\d$/.test(p))) return null;
  const times = ['morning', 'afternoon', 'night'].filter((_, i) => parts[i] !== '0');
  if (!times.length) return null;
  const text = times.join(', ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}
