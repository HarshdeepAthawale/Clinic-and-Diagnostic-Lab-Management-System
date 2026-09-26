'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { logout, useMe } from '@/lib/auth';
import { ROLE_HOME } from '@/lib/roleRoutes';

/**
 * Loads the current user for a role area. If the server says the session is gone (expired,
 * deactivated), it clears the stale cookie and returns to /login — without this, proxy.js would
 * keep bouncing between /login and the area. A user in the wrong area is sent to their own.
 */
export function useSession(expectedRole) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const me = useMe();

  useEffect(() => {
    if (me.error?.status === 401) {
      logout(queryClient);
    } else if (me.data && me.data.role !== expectedRole) {
      router.replace(ROLE_HOME[me.data.role] ?? '/login');
    }
  }, [me.error, me.data, expectedRole, router, queryClient]);

  return me;
}
