'use client';

import { Group, Skeleton, Stack, Text } from '@mantine/core';
import { IconFlask } from '@tabler/icons-react';
import { usePatientLabOrders } from '@/lib/lab';
import { formatDate } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { ListRow } from '@/components/dashboard/widgets/ListRow';
import { UrgentBadge } from './OrderLines';

/** A patient's lab orders, newest first. `hrefFor(order)` decides where a row links. */
export function LabOrderHistory({ patientId, hrefFor, limit = 6 }) {
  const orders = usePatientLabOrders(patientId);
  if (orders.isPending) return <Stack gap="xs">{[0, 1].map((i) => <Skeleton key={i} height={44} radius="md" />)}</Stack>;
  if (orders.isError) return <Text size="sm" c="var(--text-muted)">Lab orders aren&apos;t available.</Text>;
  if (orders.data.length === 0) {
    return (
      <EmptyState icon={IconFlask} title="No lab tests ordered" compact>
        Tests ordered during visits appear here.
      </EmptyState>
    );
  }
  return (
    <Stack gap={2}>
      {orders.data.slice(0, limit).map((o) => (
        <ListRow
          key={o.id}
          href={hrefFor?.(o)}
          title={o.testNames.join(', ')}
          subtitle={`${o.orderCode} · ${formatDate(o.createdAt)} · ${o.doctorName}`}
          right={
            <Group gap={4} wrap="nowrap">
              {o.status === 'CANCELLED' ? (
                <StatusBadge status="rejected" label="Cancelled" />
              ) : o.priority === 'URGENT' ? (
                <UrgentBadge />
              ) : (
                <StatusBadge status="to-collect" label="Ordered" />
              )}
            </Group>
          }
        />
      ))}
    </Stack>
  );
}
