'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';

/** Data hooks for consultations, prescriptions and the formulary (Docs/API.md "Consultations & Prescriptions"). */

export const consultationKeys = {
  all: ['consultations'],
  detail: (id) => ['consultations', id],
  mine: (today, page) => ['consultations', 'mine', today, page],
  forPatient: (patientId) => ['patients', patientId, 'consultations'],
  myPrescriptions: ['prescriptions', 'mine'],
  prescription: (id) => ['prescriptions', id],
  formulary: (q) => ['formulary', q],
};

/** Same-origin URL of a prescription PDF; the auth cookie goes with it, so a plain link works. */
export function prescriptionPdfUrl(prescriptionId, download = false) {
  return `/api/prescriptions/${prescriptionId}/pdf${download ? '?download=true' : ''}`;
}

/** Start (or reopen) the consultation for an appointment. Calls a checked-in patient in. */
export function useStartConsultation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (appointmentId) => api('/consultations', { method: 'POST', body: { appointmentId } }),
    onSuccess: (consultation) => {
      queryClient.setQueryData(consultationKeys.detail(consultation.id), consultation);
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      queryClient.invalidateQueries({ queryKey: ['queue'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useConsultation(id) {
  return useQuery({
    queryKey: consultationKeys.detail(id),
    queryFn: ({ signal }) => api(`/consultations/${id}`, { signal }),
    retry: false,
    refetchOnWindowFocus: false,
  });
}

/** Autosave: replaces the draft. Keeps the cache in step without refetching. */
export function useSaveConsultation(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(`/consultations/${id}`, { method: 'PUT', body }),
    onSuccess: (consultation) => queryClient.setQueryData(consultationKeys.detail(id), consultation),
  });
}

export function useCompleteConsultation(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(`/consultations/${id}/complete`, { method: 'POST', body }),
    onSuccess: (consultation) => {
      queryClient.setQueryData(consultationKeys.detail(id), consultation);
      queryClient.invalidateQueries({ queryKey: consultationKeys.all });
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      queryClient.invalidateQueries({ queryKey: ['queue'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: consultationKeys.forPatient(consultation.patient.id) });
    },
  });
}

export function useMyConsultations(today = false, page = 0, size = 20) {
  return useQuery({
    queryKey: consultationKeys.mine(today, page),
    queryFn: ({ signal }) =>
      api(`/consultations?${new URLSearchParams({ today: String(today), page: String(page), size: String(size) })}`, { signal }),
    placeholderData: keepPreviousData,
  });
}

/** A patient's completed visits (doctor with a care relationship, or the patient themself). */
export function usePatientConsultations(patientId, enabled = true) {
  return useQuery({
    queryKey: consultationKeys.forPatient(patientId),
    queryFn: ({ signal }) => api(`/patients/${patientId}/consultations`, { signal }),
    enabled: Boolean(patientId) && enabled,
    retry: false,
  });
}

export function useMyPrescriptions() {
  return useQuery({
    queryKey: consultationKeys.myPrescriptions,
    queryFn: ({ signal }) => api('/prescriptions/mine', { signal }),
  });
}

export function usePrescription(id) {
  return useQuery({
    queryKey: consultationKeys.prescription(id),
    queryFn: ({ signal }) => api(`/prescriptions/${id}`, { signal }),
    retry: false,
  });
}

export function useFormulary(q) {
  return useQuery({
    queryKey: consultationKeys.formulary(q),
    queryFn: ({ signal }) => api(`/formulary?${new URLSearchParams({ q })}`, { signal }),
    enabled: q.trim().length >= 2,
    staleTime: 60 * 60_000,
    placeholderData: keepPreviousData,
  });
}
