'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';
import { CLINIC_TIME_ZONE } from './format';

/** Data hooks for appointments, the live queue and doctor working hours (Docs/API.md "Appointments"). */

export const appointmentKeys = {
  all: ['appointments'],
  list: (from, to, doctorId) => ['appointments', 'list', from, to, doctorId ?? 'all'],
  mine: ['appointments', 'mine'],
  detail: (id) => ['appointments', id],
  queue: ['queue'],
  doctors: ['doctors'],
  slots: (doctorId, date) => ['doctors', doctorId, 'slots', date],
  hours: (doctorId) => ['doctors', doctorId, 'hours'],
};

/** How often live views refresh. The queue changes minute to minute at a busy front desk. */
export const LIVE_REFRESH_MS = 10_000;

export function useDoctors() {
  return useQuery({
    queryKey: appointmentKeys.doctors,
    queryFn: ({ signal }) => api('/doctors', { signal }),
    staleTime: 5 * 60_000,
  });
}

/** The logged-in doctor's own profile ({ id, fullName, specialization, hasWorkingHours }). */
export function useMyDoctorProfile() {
  return useQuery({ queryKey: ['doctors', 'me'], queryFn: ({ signal }) => api('/doctors/me', { signal }) });
}

export function useSlots(doctorId, date) {
  return useQuery({
    queryKey: appointmentKeys.slots(doctorId, date),
    queryFn: ({ signal }) => api(`/doctors/${doctorId}/slots?date=${date}`, { signal }),
    enabled: Boolean(doctorId && date),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });
}

export function useAppointments({ from, to, doctorId }, { live = false } = {}) {
  const params = new URLSearchParams({ from, to });
  if (doctorId) params.set('doctorId', doctorId);
  return useQuery({
    queryKey: appointmentKeys.list(from, to, doctorId),
    queryFn: ({ signal }) => api(`/appointments?${params}`, { signal }),
    placeholderData: keepPreviousData,
    refetchInterval: live ? LIVE_REFRESH_MS : false,
  });
}

export function useMyAppointments() {
  return useQuery({ queryKey: appointmentKeys.mine, queryFn: ({ signal }) => api('/appointments/mine', { signal }) });
}

export function useAppointment(id) {
  return useQuery({
    queryKey: appointmentKeys.detail(id),
    queryFn: ({ signal }) => api(`/appointments/${id}`, { signal }),
    enabled: Boolean(id),
  });
}

export function useQueue() {
  return useQuery({
    queryKey: appointmentKeys.queue,
    queryFn: ({ signal }) => api('/queue', { signal }),
    refetchInterval: LIVE_REFRESH_MS,
    refetchIntervalInBackground: true,
  });
}

export function useWorkingHours(doctorId) {
  return useQuery({
    queryKey: appointmentKeys.hours(doctorId),
    queryFn: ({ signal }) => api(`/doctors/${doctorId}/working-hours`, { signal }),
    enabled: Boolean(doctorId),
  });
}

/** Anything that changes an appointment refreshes every view that shows appointments. */
function useInvalidateAppointments() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: appointmentKeys.all });
    queryClient.invalidateQueries({ queryKey: appointmentKeys.queue });
    queryClient.invalidateQueries({ queryKey: ['doctors'] });
    queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  };
}

export function useBookAppointment() {
  const invalidate = useInvalidateAppointments();
  return useMutation({
    mutationFn: (body) => api('/appointments', { method: 'POST', body }),
    onSuccess: invalidate,
  });
}

export function useIssueToken() {
  const invalidate = useInvalidateAppointments();
  return useMutation({
    mutationFn: (body) => api('/queue/tokens', { method: 'POST', body }),
    onSuccess: invalidate,
  });
}

export function useChangeStatus() {
  const invalidate = useInvalidateAppointments();
  return useMutation({
    mutationFn: ({ id, status, note }) => api(`/appointments/${id}/status`, { method: 'PATCH', body: { status, note } }),
    onSuccess: invalidate,
    // A failed move (e.g. someone else already called the patient) means our view is stale.
    onError: invalidate,
  });
}

export function useSaveWorkingHours(doctorId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (blocks) => api(`/doctors/${doctorId}/working-hours`, { method: 'PUT', body: { blocks } }),
    onSuccess: (hours) => {
      queryClient.setQueryData(appointmentKeys.hours(doctorId), hours);
      queryClient.invalidateQueries({ queryKey: ['doctors'] });
    },
  });
}

// ------------------------------------------------------------------ dates (clinic time zone)


/** "2026-09-27" for the clinic's today (or `offset` days from it). */
export function clinicDate(offset = 0, from = new Date()) {
  const d = new Date(from.getTime() + offset * 86_400_000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: CLINIC_TIME_ZONE }).format(d);
}

/** Adds days to a "YYYY-MM-DD" string. */
export function addDays(isoDate, days) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}

/** Monday of the week containing `isoDate`. */
export function weekStart(isoDate) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const weekday = new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // 0 = Sunday
  return addDays(isoDate, weekday === 0 ? -6 : 1 - weekday);
}

/** "Mon 28 Sept" for a "YYYY-MM-DD" string. */
export function dayLabel(isoDate, options = { weekday: 'short', day: 'numeric', month: 'short' }) {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d, 12)).toLocaleDateString('en-GB', { ...options, timeZone: 'UTC' });
}

/** Minutes a checked-in patient has been waiting. */
export function minutesWaiting(checkedInAt, now = Date.now()) {
  return Math.max(0, Math.floor((now - new Date(checkedInAt).getTime()) / 60_000));
}
