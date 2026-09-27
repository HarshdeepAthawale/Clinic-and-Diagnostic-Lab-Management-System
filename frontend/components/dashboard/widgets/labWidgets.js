'use client';

import { Box, Group, Stack, Text } from '@mantine/core';
import { IconFlask, IconTestPipe } from '@tabler/icons-react';
import Link from 'next/link';
import { formatDate } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { Panel } from '@/components/ui/Panel';
import { TUBES, TubeChip } from '@/components/ui/TubeChip';
import { OrderCode, UrgentBadge } from '@/components/lab/OrderLines';
import { PrepChecklist } from '@/components/lab/PrepChecklist';
import { QueueRow } from '@/components/lab/views';

const linkStyle = { fontSize: 13, fontWeight: 600, color: 'var(--accent)' };

/** Lab: the newest part of the queue, urgent first. */
export function IncomingOrders({ widget }) {
  const { orders, total } = widget.data;
  return (
    <Panel
      title={widget.title}
      subtitle={total ? `${total} waiting · urgent first` : 'Nothing waiting'}
      right={<Link href="/lab/orders" style={linkStyle}>All orders</Link>}
    >
      {orders.length === 0 ? (
        <EmptyState icon={IconFlask} title="No orders waiting" compact>
          Orders arrive here the moment a doctor requests tests.
        </EmptyState>
      ) : (
        <Stack gap={2}>
          {orders.map((o) => <QueueRow key={o.id} order={o} href={`/lab/orders/${o.id}`} />)}
        </Stack>
      )}
    </Panel>
  );
}

/** Lab: how many of each tube the open orders need, as bars in the tubes' own cap colours. */
export function TubesNeeded({ widget }) {
  const entries = Object.entries(widget.data);
  const max = Math.max(1, ...entries.map(([, n]) => n));
  return (
    <Panel title={widget.title} subtitle="Across all open orders">
      {entries.length === 0 ? (
        <EmptyState icon={IconTestPipe} title="No tubes needed" compact>
          Tubes to prepare show up as tests are ordered.
        </EmptyState>
      ) : (
        <Stack gap="md">
          {entries.map(([tube, count]) => (
            <div key={tube}>
              <Group justify="space-between" mb={6}>
                <TubeChip tube={tube} />
                <Text size="sm" fw={600} className="mono">{count}</Text>
              </Group>
              <Box h={6} style={{ borderRadius: 999, background: 'var(--surface-2)', overflow: 'hidden' }}>
                <Box
                  h="100%"
                  style={{
                    width: `${(count / max) * 100}%`,
                    borderRadius: 999,
                    background: TUBES[tube]?.cap === '#FFFFFF' ? 'var(--border-strong)' : TUBES[tube]?.cap ?? 'var(--ink)',
                    transition: 'width var(--duration-base) var(--ease-out)',
                  }}
                />
              </Box>
            </div>
          ))}
        </Stack>
      )}
    </Panel>
  );
}

/** Patient: open orders with what to do before the test, so fasting instructions aren't missed. */
export function MyLabOrders({ widget }) {
  return (
    <GlowCard p="lg">
      <Group justify="space-between" mb="md">
        <Group gap={8}>
          <IconFlask size={18} color="var(--accent)" />
          <Text fw={600}>{widget.title}</Text>
        </Group>
        <Link href="/patient/lab-tests" style={linkStyle}>Details</Link>
      </Group>
      <Stack gap="lg">
        {widget.data.map((order) => (
          <Stack key={order.id} gap="sm">
            <Group justify="space-between" wrap="wrap" gap="xs">
              <Text size="sm">
                <Text span fw={600}>{order.items.map((i) => i.name).join(', ')}</Text>
                <Text span c="var(--text-muted)"> · {order.doctor.fullName}, {formatDate(order.createdAt)}</Text>
              </Text>
              <Group gap={6}>
                {order.priority === 'URGENT' && <UrgentBadge />}
                <OrderCode code={order.orderCode} />
              </Group>
            </Group>
            <PrepChecklist items={order.items} />
          </Stack>
        ))}
      </Stack>
    </GlowCard>
  );
}
