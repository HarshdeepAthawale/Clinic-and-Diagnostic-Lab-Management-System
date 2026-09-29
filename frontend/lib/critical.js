'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';
import { flagLabel } from './results';

/** Critical value alerts (Docs/API.md "Reports", ADR-028). */

export const criticalKeys = {
  mine: ['critical', 'mine'],
  all: ['critical', 'all'],
};

/** "Haemoglobin — critical low"; the number is on the report, which is logged when opened. */
export function parameterText(parameter) {
  return `${parameter.name} — ${flagLabel(parameter.flag).toLowerCase()}`;
}

/** "Haemoglobin — critical low; Potassium — critical high" */
export function parametersText(parameters) {
  return parameters.map(parameterText).join('; ');
}

/** How long a critical result has waited, in words: "5 min", "2 h 10 min", "1 d 3 h". */
export function waitingFor(verifiedAt, now = Date.now()) {
  const minutes = Math.max(0, Math.round((now - new Date(verifiedAt).getTime()) / 60_000));
  if (minutes < 60) return `${minutes} min`;
  if (minutes < 24 * 60) {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m ? `${h} h ${m} min` : `${h} h`;
  }
  const d = Math.floor(minutes / (24 * 60));
  const h = Math.round((minutes % (24 * 60)) / 60);
  return h ? `${d} d ${h} h` : `${d} d`;
}

/** Waiting longer than this is shown as overdue, in the alert's own colour. */
export const OVERDUE_MINUTES = 60;

export function isOverdue(verifiedAt, now = Date.now()) {
  return now - new Date(verifiedAt).getTime() > OVERDUE_MINUTES * 60_000;
}

/** The doctor's own waiting critical results (`role` DOCTOR) or everyone's (LAB_TECHNICIAN). */
export function useCriticalAlerts(role, enabled = true) {
  const lab = role === 'LAB_TECHNICIAN';
  return useQuery({
    queryKey: lab ? criticalKeys.all : criticalKeys.mine,
    queryFn: ({ signal }) => api(lab ? '/reports/critical/all' : '/reports/critical', { signal }),
    enabled: enabled && (role === 'DOCTOR' || lab),
    refetchInterval: 60_000,
  });
}

export function useAcknowledgeCritical() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sampleId, note }) => api(`/reports/${sampleId}/acknowledge-critical`, { method: 'POST', body: { note: note?.trim() || null } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['critical'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['reports'] });
    },
  });
}
