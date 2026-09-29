'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';

/** Data hooks and helpers for the lab's consumables (Docs/API.md "Inventory", ADR-026). */

export const inventoryKeys = {
  all: ['inventory'],
  list: (filters) => ['inventory', 'list', filters],
  alerts: ['inventory', 'alerts'],
  movements: (id) => ['inventory', 'movements', id],
};

export const CATEGORIES = [
  { value: 'TUBE', label: 'Tubes & containers' },
  { value: 'REAGENT', label: 'Reagents' },
  { value: 'CONSUMABLE', label: 'Consumables' },
  { value: 'OTHER', label: 'Other' },
];

export function categoryLabel(category) {
  return CATEGORIES.find((c) => c.value === category)?.label ?? category;
}

/**
 * Why a level changes. `sign` is the direction the number must have (1 adds, -1 takes away, 0 either), so
 * the form can take a plain positive quantity and apply the direction itself.
 */
export const REASONS = {
  RESTOCK: { label: 'Restock', hint: 'Stock received', sign: 1 },
  USED: { label: 'Used', hint: 'Used at the bench', sign: -1 },
  WASTAGE: { label: 'Wastage', hint: 'Spilled, broken or expired', sign: -1 },
  CORRECTION: { label: 'Correction', hint: 'The count was wrong', sign: 0 },
};

export const REASON_LABELS = { OPENING: 'Opening stock', ...Object.fromEntries(Object.entries(REASONS).map(([k, v]) => [k, v.label])) };

/** 'out' when empty and watched, 'low' when below the threshold, else 'ok'. Matches the server's rule. */
export function stockState(item) {
  if (!item.active) return 'retired';
  if (item.outOfStock) return 'out';
  if (item.lowStock) return 'low';
  return 'ok';
}

/** How full an item is against twice its threshold, 0–100, for the level bar; unwatched items have no bar. */
export function stockFill(item) {
  if (!item.lowStockThreshold) return null;
  return Math.max(0, Math.min(100, Math.round((item.currentStock / (item.lowStockThreshold * 2)) * 100)));
}

/**
 * The signed change for a form entry: a restock adds, use and wastage take away; a correction is entered as
 * the new direction by the caller. Returns null for anything that isn't a whole number above zero.
 */
export function deltaFor(reason, quantity, direction = 1) {
  const n = Number(quantity);
  if (!Number.isInteger(n) || n <= 0) return null;
  const sign = REASONS[reason]?.sign ?? 0;
  return (sign === 0 ? direction : sign) * n;
}

/** What is wrong with an adjustment before it is sent, or null when fine. Server rules are the authority. */
export function adjustProblem({ reason, quantity, direction = 1, item }) {
  const delta = deltaFor(reason, quantity, direction);
  if (delta === null) return 'Enter a whole number above zero';
  if (delta > 1_000_000) return 'That is too many at once';
  if (item && item.currentStock + delta < 0) {
    return `Only ${item.currentStock} ${item.unit} left — that would take it below zero`;
  }
  return null;
}

/** "+12" / "−3" with a real minus sign. */
export function formatDelta(delta) {
  return delta > 0 ? `+${delta}` : `−${Math.abs(delta)}`;
}

// ------------------------------------------------------------------ queries

export function useInventory({ q = '', category = null, lowOnly = false, includeInactive = false } = {}) {
  const params = new URLSearchParams({ q, lowOnly: String(lowOnly), includeInactive: String(includeInactive) });
  if (category) params.set('category', category);
  return useQuery({
    queryKey: inventoryKeys.list(params.toString()),
    queryFn: ({ signal }) => api(`/inventory?${params}`, { signal }),
    placeholderData: keepPreviousData,
  });
}

/** Counts and the emptiest items: drives the dashboard card and the bell. */
export function useInventoryAlerts(enabled = true) {
  return useQuery({
    queryKey: inventoryKeys.alerts,
    queryFn: ({ signal }) => api('/inventory/alerts', { signal }),
    enabled,
    refetchInterval: 120_000,
  });
}

export function useMovements(id) {
  return useQuery({
    queryKey: inventoryKeys.movements(id),
    queryFn: ({ signal }) => api(`/inventory/${id}/movements`, { signal }),
    enabled: Boolean(id),
  });
}

// ------------------------------------------------------------------ changes

function refresh(queryClient) {
  queryClient.invalidateQueries({ queryKey: inventoryKeys.all });
  queryClient.invalidateQueries({ queryKey: ['dashboard'] });
}

export function useSaveItem(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(id ? `/inventory/${id}` : '/inventory', { method: id ? 'PUT' : 'POST', body }),
    onSuccess: () => refresh(queryClient),
  });
}

export function useAdjustStock(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => api(`/inventory/${id}/stock`, { method: 'PATCH', body }),
    onSuccess: () => refresh(queryClient),
  });
}
