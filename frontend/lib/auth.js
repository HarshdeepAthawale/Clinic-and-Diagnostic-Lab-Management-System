'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';

export const ME_KEY = ['me'];

/** The logged-in user: `{ id, email, role, name }`. Errors with code UNAUTHENTICATED when logged out. */
export function useMe() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: ({ signal }) => api('/auth/me', { signal }),
    retry: false,
    staleTime: 5 * 60_000,
  });
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (credentials) => api('/auth/login', { method: 'POST', body: credentials }),
    onSuccess: (me) => queryClient.setQueryData(ME_KEY, me),
  });
}

export function useRegister() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (details) => api('/auth/register', { method: 'POST', body: details }),
    onSuccess: (me) => queryClient.setQueryData(ME_KEY, me),
  });
}

/** Clears the cookie server-side, drops all cached data, and does a full reload to /login. */
export async function logout(queryClient) {
  try {
    await api('/auth/logout', { method: 'POST' });
  } finally {
    queryClient?.clear();
    window.location.assign('/login');
  }
}
