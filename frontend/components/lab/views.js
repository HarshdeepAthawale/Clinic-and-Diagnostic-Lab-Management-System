'use client';

import { Alert, Box, Grid, Group, Pagination, Skeleton, Stack, Text } from '@mantine/core';
import { IconAlertCircle, IconFlask, IconNotes, IconStethoscope } from '@tabler/icons-react';
import { useState } from 'react';
import { useLabOrder, useLabQueue, useMyLabOrders } from '@/lib/lab';
import { friendlyMessage } from '@/lib/errors';
import { ageGender, formatDate, formatDateTime, formatMoney, formatRelative } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { Panel } from '@/components/ui/Panel';
import { Reveal } from '@/components/ui/Reveal';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { TubeChip } from '@/components/ui/TubeChip';
import { ListRow } from '@/components/dashboard/widgets/ListRow';
import { OrderCode, OrderLines, TubeRow, UrgentBadge } from './OrderLines';
import { PrepChecklist } from './PrepChecklist';

function ListSkeleton({ rows = 3, height = 56 }) {
  return <Stack gap="xs">{Array.from({ length: rows }, (_, i) => <Skeleton key={i} height={height} radius="md" />)}</Stack>;
}

function OrderStatus({ order }) {
  if (order.status === 'CANCELLED') return <StatusBadge status="rejected" label="Cancelled" />;
  return <StatusBadge status="to-collect" label="Awaiting sample" />;
}

// ------------------------------------------------------------------ patient

/** One order as the patient sees it: prep first, then the tests and what they cost. */
function PatientOrderCard({ order, index }) {
  const open = order.status === 'ORDERED';
  return (
    <Reveal delay={Math.min(index, 6) * 0.04}>
      <GlowCard p="lg" style={open ? undefined : { opacity: 0.7 }}>
        <Group justify="space-between" align="flex-start" wrap="wrap" gap="sm" mb="md">
          <div>
            <Group gap={8}>
              <Text fw={600}>{order.items.length} test{order.items.length === 1 ? '' : 's'} ordered</Text>
              {order.priority === 'URGENT' && open && <UrgentBadge />}
            </Group>
            <Text size="sm" c="var(--text-muted)">
              {order.doctor.fullName} · {formatDate(order.createdAt)}
            </Text>
          </div>
          <Group gap={6}>
            <OrderCode code={order.orderCode} />
            <OrderStatus order={order} />
          </Group>
        </Group>
        <Stack gap="md">
          {open && <PrepChecklist items={order.items} />}
          <OrderLines order={order} showPrep={!open} showTubes={false} />
          {open && (
            <Text size="xs" c="var(--text-subtle)">
              Show the order number <span className="mono">{order.orderCode}</span> at the lab counter. Results appear under Reports once a pathologist has checked them.
            </Text>
          )}
          {!open && order.cancellationReason && (
            <Text size="xs" c="var(--text-muted)">Cancelled {order.cancelledAt ? formatDate(order.cancelledAt) : ''} — {order.cancellationReason}</Text>
          )}
        </Stack>
      </GlowCard>
    </Reveal>
  );
}

export function PatientLabOrdersView() {
  const orders = useMyLabOrders();
  const open = orders.data?.filter((o) => o.status === 'ORDERED') ?? [];
  const past = orders.data?.filter((o) => o.status !== 'ORDERED') ?? [];
  return (
    <Stack gap="xl">
      <PageTitle title="Lab tests" subtitle="Tests your doctors have ordered, with anything you need to do beforehand." />
      {orders.isPending ? (
        <ListSkeleton rows={2} height={160} />
      ) : orders.isError ? (
        <Text c="var(--critical)" size="sm">{friendlyMessage(orders.error)}</Text>
      ) : orders.data.length === 0 ? (
        <GlowCard p="lg">
          <EmptyState icon={IconFlask} title="No lab tests ordered">
            When a doctor orders blood or urine tests, they appear here with any preparation, like fasting.
          </EmptyState>
        </GlowCard>
      ) : (
        <Stack gap="lg">
          {open.map((o, i) => <PatientOrderCard key={o.id} order={o} index={i} />)}
          {past.length > 0 && (
            <>
              <Text size="xs" fw={700} tt="uppercase" c="var(--text-subtle)" style={{ letterSpacing: '0.06em' }} mt="sm">Cancelled</Text>
              {past.map((o, i) => <PatientOrderCard key={o.id} order={o} index={open.length + i} />)}
            </>
          )}
        </Stack>
      )}
    </Stack>
  );
}

// ------------------------------------------------------------------ lab

/** A queue row: urgency, patient, tests, tubes, how long it has waited. */
export function QueueRow({ order, href }) {
  return (
    <ListRow
      href={href}
      leading={
        <Box w={4} h={36} style={{ borderRadius: 4, flex: 'none', background: order.priority === 'URGENT' ? 'var(--critical)' : 'var(--border-strong)' }} />
      }
      title={order.patientName}
      subtitle={`${order.orderCode} · ${order.testNames.join(', ')} · ${formatRelative(order.createdAt)}`}
      right={
        <Group gap={4} wrap="nowrap">
          {order.priority === 'URGENT' && <UrgentBadge />}
          <Group gap={4} wrap="nowrap" visibleFrom="sm">
            {order.tubes.map((t) => <TubeChip key={t} tube={t} />)}
          </Group>
        </Group>
      }
    />
  );
}

export function LabQueueView() {
  const [page, setPage] = useState(1);
  const queue = useLabQueue(page - 1);
  const totalPages = queue.data ? Math.ceil(queue.data.totalElements / queue.data.size) : 0;
  return (
    <Stack gap="xl">
      <PageTitle
        title="Orders"
        subtitle="Every open lab order, urgent first then oldest first. New orders appear here as doctors place them."
        actions={
          queue.data && (
            <Group gap={8}>
              <span className="live-dot" />
              <Text size="sm" c="var(--text-muted)">{queue.data.totalElements} waiting</Text>
            </Group>
          )
        }
      />
      <GlowCard p="lg">
        {queue.isPending ? (
          <ListSkeleton rows={4} />
        ) : queue.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(queue.error)}</Text>
        ) : queue.data.content.length === 0 ? (
          <EmptyState icon={IconFlask} title="No orders waiting">
            Orders arrive here the moment a doctor requests tests.
          </EmptyState>
        ) : (
          <Stack gap={2}>
            {queue.data.content.map((o) => <QueueRow key={o.id} order={o} href={`/lab/orders/${o.id}`} />)}
          </Stack>
        )}
        {totalPages > 1 && (
          <Group justify="center" mt="md">
            <Pagination total={totalPages} value={page} onChange={setPage} size="sm" color="dark" />
          </Group>
        )}
      </GlowCard>
    </Stack>
  );
}

/** One order in full, for the lab (and doctors): who, what, which tubes, the doctor's note, the prep. */
export function LabOrderDetailView({ id, back }) {
  const query = useLabOrder(id);
  if (query.isPending) return <ListSkeleton rows={2} height={180} />;
  if (query.isError) {
    return (
      <Alert color="red" variant="light" radius="lg" icon={<IconAlertCircle size={18} />} title="Couldn't open this order">
        {query.error?.status === 404 ? "This order doesn't exist or isn't yours to see." : friendlyMessage(query.error)}
      </Alert>
    );
  }
  const order = query.data;
  const p = order.patient;
  return (
    <Stack gap="xl">
      <PageTitle
        title={<span className="mono">{order.orderCode}</span>}
        subtitle={`Ordered ${formatDateTime(order.createdAt)}`}
        back={back}
        actions={
          <Group gap={6}>
            {order.priority === 'URGENT' && <UrgentBadge />}
            <OrderStatus order={order} />
          </Group>
        }
      />
      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: 8 }}>
          <Reveal>
            <Panel title="Tests" subtitle={`Total ${formatMoney(order.total)}`}>
              <OrderLines order={order} />
            </Panel>
          </Reveal>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 4 }}>
          <Stack gap="lg">
            <Reveal delay={0.04}>
              <Panel title="Patient">
                <Text fw={600}>{p.fullName}</Text>
                <Text size="sm" c="var(--text-muted)" className="mono">{p.patientCode} · {ageGender(p.age, p.gender)}</Text>
                <Group gap={6} mt="md">
                  <IconStethoscope size={15} color="var(--text-muted)" />
                  <Text size="sm">{order.doctor.fullName}</Text>
                </Group>
                <Text size="xs" c="var(--text-muted)" ml={21}>{order.doctor.specialization}</Text>
              </Panel>
            </Reveal>
            <Reveal delay={0.08}>
              <Panel title="Collect in" subtitle="One tube per type unless the lab's volume guide says otherwise">
                <TubeRow items={order.items} />
              </Panel>
            </Reveal>
            {order.clinicalNotes && (
              <Reveal delay={0.1}>
                <Panel title="Doctor's note" right={<IconNotes size={16} color="var(--text-muted)" />}>
                  <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{order.clinicalNotes}</Text>
                </Panel>
              </Reveal>
            )}
            <Reveal delay={0.12}>
              <PrepChecklist items={order.items} title="Check the patient has prepared" />
            </Reveal>
          </Stack>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
