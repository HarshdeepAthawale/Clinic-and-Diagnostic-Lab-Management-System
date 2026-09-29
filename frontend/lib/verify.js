'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from './api';

/** Checking a printed report from its QR code (ADR-027): open to anyone, shows no results. */

const CODE = /^[0-9a-f]{32}$/;

/** Whether text is shaped like a report code (32 lowercase hex characters); anything else can't be one. */
export function isReportCode(text) {
  return typeof text === 'string' && CODE.test(text);
}

export function verifyPath(code) {
  return `/verify/${code}`;
}

export function useReportCheck(code) {
  return useQuery({
    queryKey: ['report-check', code],
    queryFn: ({ signal }) => api(`/public/reports/${code}`, { signal }),
    enabled: isReportCode(code),
    retry: false,
    refetchOnWindowFocus: false,
    staleTime: 5 * 60_000,
  });
}
