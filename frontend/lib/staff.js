'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';

/** Staff accounts (Docs/API.md "Admin", ADR-032) and changing your own password. */

export const staffKeys = { all: ['staff'], list: (q, all) => ['staff', q, all] };

export const STAFF_ROLES = [
  { value: 'DOCTOR', label: 'Doctor' },
  { value: 'PATHOLOGIST', label: 'Pathologist' },
  { value: 'RECEPTIONIST', label: 'Receptionist' },
  { value: 'LAB_TECHNICIAN', label: 'Lab technician' },
  { value: 'ADMIN', label: 'Admin' },
];

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** What is wrong with the form before it is sent (the server still checks everything), as { field: message }. */
export function staffProblems(v) {
  const problems = {};
  if (!v.fullName?.trim()) problems.fullName = 'Required';
  if (!EMAIL.test(v.email?.trim() ?? '')) problems.email = 'Enter a valid email address';
  if (v.role === 'DOCTOR' && !v.specialization?.trim()) problems.specialization = 'A doctor needs a specialization';
  if (v.role === 'PATHOLOGIST') {
    if (!v.qualification?.trim()) problems.qualification = 'A pathologist needs a qualification';
    if (!v.registrationNumber?.trim()) problems.registrationNumber = 'A pathologist needs a registration number';
  }
  return problems;
}

/** The request body: only the details that role uses. */
export function createBody(v) {
  const body = { role: v.role, fullName: v.fullName.trim(), email: v.email.trim() };
  if (v.role === 'DOCTOR') body.specialization = v.specialization.trim();
  if (v.role === 'PATHOLOGIST') {
    body.qualification = v.qualification.trim();
    body.registrationNumber = v.registrationNumber.trim();
  }
  return body;
}

/** What is wrong with a password change, or null. Matches the server: 8 to 72 characters, different from the old one. */
export function passwordProblem({ current, next, confirm }) {
  if (!current) return { field: 'current', message: 'Enter your current password' };
  if (next.length < 8) return { field: 'next', message: 'Use at least 8 characters' };
  if (next.length > 72) return { field: 'next', message: 'Use 72 characters or fewer' };
  if (next === current) return { field: 'next', message: "Choose a password you haven't been using" };
  if (next !== confirm) return { field: 'confirm', message: "The two passwords don't match" };
  return null;
}

export function useStaff(q = '', includeInactive = true) {
  return useQuery({
    queryKey: staffKeys.list(q, includeInactive),
    queryFn: ({ signal }) => api(`/admin/staff?${new URLSearchParams({ q, includeInactive: String(includeInactive) })}`, { signal }),
    placeholderData: keepPreviousData,
  });
}

export function useCreateStaff() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api('/admin/staff', { method: 'POST', body }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: staffKeys.all });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useSetStaffActive() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, active }) => api(`/admin/staff/${userId}/active`, { method: 'PATCH', body: { active } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: staffKeys.all });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: ({ current, next }) => api('/auth/change-password', { method: 'POST', body: { currentPassword: current, newPassword: next } }),
  });
}
