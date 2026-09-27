'use client';

import { notifications } from '@mantine/notifications';
import { useRouter } from 'next/navigation';
import { useStartConsultation } from '@/lib/consultations';
import { friendlyMessage } from '@/lib/errors';

/**
 * Doctor action used everywhere a visit is started or resumed (queue board, day agenda,
 * appointment menus): start or reopen the consultation for an appointment, then go to it.
 */
export function useOpenConsultation() {
  const router = useRouter();
  const start = useStartConsultation();
  const open = (appointmentId) =>
    start.mutate(appointmentId, {
      onSuccess: (consultation) => router.push(`/doctor/consultations/${consultation.id}`),
      onError: (error) =>
        notifications.show({ title: "Couldn't open the consultation", message: friendlyMessage(error), color: 'red', radius: 'lg' }),
    });
  return { open, isPending: start.isPending, pendingFor: start.isPending ? start.variables : null };
}
