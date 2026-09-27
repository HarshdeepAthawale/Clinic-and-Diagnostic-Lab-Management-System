'use client';

import { Button, Group, Skeleton, Stack, Text } from '@mantine/core';
import { IconFlask, IconPlus } from '@tabler/icons-react';
import { useState } from 'react';
import { useConsultationLabOrder, useRemoveOrderItem } from '@/lib/lab';
import { friendlyMessage } from '@/lib/errors';
import { Panel } from '@/components/ui/Panel';
import { EmptyState } from '@/components/ui/EmptyState';
import { OrderCode, OrderLines, TubeRow, UrgentBadge } from './OrderLines';
import { OrderTestsDrawer } from './OrderTestsDrawer';

/**
 * "Lab tests" in the consult workspace: the order goes to the lab the moment it's placed — no
 * re-typing at the front desk. Adding more later in the visit extends the same order.
 */
export function ConsultLabOrder({ consultation }) {
  const [opened, setOpened] = useState(false);
  const order = useConsultationLabOrder(consultation.id);
  const remove = useRemoveOrderItem();
  const current = order.data;

  return (
    <Panel
      title="Lab tests"
      subtitle={current ? 'Sent to the lab — it appears in their queue straight away' : 'Ordered tests go straight to the lab'}
      right={
        <Group gap={6}>
          {current?.priority === 'URGENT' && <UrgentBadge />}
          {current && <OrderCode code={current.orderCode} />}
          <Button size="xs" variant={current ? 'default' : 'filled'} leftSection={<IconPlus size={14} />} onClick={() => setOpened(true)}>
            {current ? 'Add tests' : 'Order tests'}
          </Button>
        </Group>
      }
    >
      {order.isPending ? (
        <Skeleton height={48} radius="md" />
      ) : order.isError ? (
        <Text size="sm" c="var(--critical)">{friendlyMessage(order.error)}</Text>
      ) : !current ? (
        <EmptyState icon={IconFlask} title="No tests ordered" compact>
          Search the catalog or pick a panel. The patient sees any preparation, like fasting, in their app.
        </EmptyState>
      ) : (
        <Stack gap="sm">
          <TubeRow items={current.items} />
          <OrderLines
            order={current}
            removingId={remove.isPending ? remove.variables?.itemId : null}
            onRemove={(item) => remove.mutate({ orderId: current.id, itemId: item.id })}
          />
          {remove.error && <Text size="xs" c="var(--critical)">{friendlyMessage(remove.error)}</Text>}
        </Stack>
      )}

      <OrderTestsDrawer
        opened={opened}
        onClose={() => setOpened(false)}
        patient={consultation.patient}
        consultationId={consultation.id}
        orderedTestIds={current?.items.map((i) => i.testId) ?? []}
      />
    </Panel>
  );
}
