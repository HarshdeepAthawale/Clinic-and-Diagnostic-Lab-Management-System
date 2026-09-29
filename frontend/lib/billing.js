'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';

/** Data hooks for invoices, payments and discounts (Docs/API.md "Billing"). */

export const billingKeys = {
  all: ['invoices'],
  list: (status, q, page) => ['invoices', 'list', status, q, page],
  detail: (id) => ['invoices', id],
  mine: ['invoices', 'mine'],
  forPatient: (patientId) => ['patients', patientId, 'invoices'],
};

// ------------------------------------------------------------------ pure helpers

export const PAYMENT_METHODS = [
  { value: 'CASH', label: 'Cash' },
  { value: 'CARD', label: 'Card' },
  { value: 'UPI', label: 'UPI' },
];

/** Invoice status → StatusBadge status + label. */
export const INVOICE_STATUS = {
  UNPAID: { status: 'due', label: 'Unpaid' },
  PARTIALLY_PAID: { status: 'waiting', label: 'Part paid' },
  PAID: { status: 'paid', label: 'Paid' },
  VOID: { status: 'rejected', label: 'Void' },
};

/** Amounts are decimals from the API; work in paise so sums and comparisons never drift. */
export const toPaise = (amount) => Math.round(Number(amount ?? 0) * 100);
export const fromPaise = (paise) => paise / 100;

/**
 * Checks a payment amount typed at the counter against the invoice's balance.
 * Returns an error message, or null when it's fine.
 */
export function paymentError(value, balance) {
  const paise = toPaise(value);
  if (value === '' || value === null || !Number.isFinite(Number(value)) || paise <= 0) return 'Enter an amount';
  if (Math.abs(Number(value) * 100 - paise) > 1e-6) return 'Use at most two decimal places';
  if (paise > toPaise(balance)) return `More than the balance (${balance})`;
  return null;
}

/** The most the front desk may discount on a bill of `gross` at `capPercent` %, rounded down to the paisa. */
export function receptionDiscountCap(gross, capPercent) {
  return fromPaise(Math.floor((toPaise(gross) * Number(capPercent)) / 100));
}

/**
 * Checks a discount before it's sent. `canExceedCap` is true for admins.
 * Returns an error message, or null when it's fine (0 removes the discount).
 */
export function discountError({ amount, reason, gross, paid, capPercent, canExceedCap }) {
  if (amount === '' || amount === null || !Number.isFinite(Number(amount)) || Number(amount) < 0) return 'Enter an amount';
  const paise = toPaise(amount);
  if (paise === 0) return null;
  if (!reason?.trim()) return 'Say why the discount is given';
  if (paise > toPaise(gross)) return "The discount can't be more than the bill";
  if (toPaise(gross) - paise < toPaise(paid)) return 'The patient has already paid more than the bill would be';
  if (!canExceedCap && paise > toPaise(receptionDiscountCap(gross, capPercent))) {
    return `The front desk can discount up to ${capPercent}%. Ask an admin for more.`;
  }
  return null;
}

/** Quick discount choices as amounts: 5, 10, 15 % … capped at what the caller may give. */
export function discountPresets(gross, capPercent, canExceedCap) {
  const max = canExceedCap ? 100 : Number(capPercent);
  return [5, 10, 15, 20].filter((p) => p <= max).map((percent) => ({
    percent,
    amount: fromPaise(Math.floor((toPaise(gross) * percent) / 100)),
  }));
}

// ------------------------------------------------------------------ counter

/** The counter list. `status` is OUTSTANDING, PAID or ALL; refreshed every 30 s. */
export function useInvoices(status = 'OUTSTANDING', q = '', page = 0, size = 20) {
  return useQuery({
    queryKey: billingKeys.list(status, q, page),
    queryFn: ({ signal }) =>
      api(`/invoices?${new URLSearchParams({ status, q, page: String(page), size: String(size) })}`, { signal }),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });
}

export function useInvoice(id) {
  return useQuery({
    queryKey: billingKeys.detail(id),
    queryFn: ({ signal }) => api(`/invoices/${id}`, { signal }),
    retry: false,
  });
}

function afterChange(queryClient, invoice) {
  queryClient.setQueryData(billingKeys.detail(invoice.id), invoice);
  queryClient.invalidateQueries({ queryKey: [...billingKeys.all, 'list'] });
  queryClient.invalidateQueries({ queryKey: billingKeys.mine });
  queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  queryClient.invalidateQueries({ queryKey: billingKeys.forPatient(invoice.patient.id) });
}

export function usePayInvoice(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(`/invoices/${id}/payments`, { method: 'POST', body }),
    onSuccess: (invoice) => afterChange(queryClient, invoice),
  });
}

export function useDiscountInvoice(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(`/invoices/${id}/discount`, { method: 'POST', body }),
    onSuccess: (invoice) => afterChange(queryClient, invoice),
  });
}

// ------------------------------------------------------------------ patient

export function useMyInvoices() {
  return useQuery({
    queryKey: billingKeys.mine,
    queryFn: ({ signal }) => api('/invoices/mine', { signal }),
  });
}

/** A patient's invoices as seen by the front desk. */
export function usePatientInvoices(patientId, enabled = true) {
  return useQuery({
    queryKey: billingKeys.forPatient(patientId),
    queryFn: ({ signal }) => api(`/patients/${patientId}/invoices`, { signal }),
    enabled: Boolean(patientId) && enabled,
    retry: false,
  });
}

/** Same-origin URL of an invoice PDF; the auth cookie goes with it, so a plain link works. */
export function invoicePdfUrl(invoiceId, download = false) {
  return `/api/invoices/${invoiceId}/pdf${download ? '?download=true' : ''}`;
}
