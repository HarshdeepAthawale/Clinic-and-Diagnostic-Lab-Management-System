'use client';

import { Divider, Group, Stack, Text } from '@mantine/core';
import { IconCircleCheck, IconPhoneCall } from '@tabler/icons-react';
import Link from 'next/link';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { Panel } from '@/components/ui/Panel';
import { RedrawAlerts } from '@/components/lab/RedrawAlerts';
import { SampleRow } from '@/components/lab/SampleBits';

const linkStyle = { fontSize: 13, fontWeight: 600, color: 'var(--accent)' };

function Section({ title, total, rows }) {
  return (
    <div>
      <Group justify="space-between" mb={4} px="sm">
        <Text size="xs" fw={700} tt="uppercase" c="var(--text-subtle)" style={{ letterSpacing: '0.06em' }}>{title}</Text>
        <Text size="xs" className="mono" c="var(--text-muted)">{total}</Text>
      </Group>
      {rows.length === 0 ? (
        <Text size="sm" c="var(--text-subtle)" px="sm" py={6}>Nothing waiting.</Text>
      ) : (
        <Stack gap={2}>{rows.map((s) => <SampleRow key={s.id} sample={s} href={`/lab/samples/${s.id}`} />)}</Stack>
      )}
    </div>
  );
}

/** Lab: what to draw and what to check in next — urgent orders and redraws first. */
export function SampleQueue({ widget }) {
  const d = widget.data;
  const empty = d.collectTotal === 0 && d.receiveTotal === 0;
  return (
    <Panel
      title={widget.title}
      subtitle="Urgent orders and redraws first"
      right={<Link href="/lab/samples" style={linkStyle}>Scan or open a sample</Link>}
    >
      {empty ? (
        <EmptyState icon={IconCircleCheck} title="Bench is clear" compact>
          Samples appear here the moment a doctor orders tests.
        </EmptyState>
      ) : (
        <Stack gap="md">
          <Section title="To collect" total={d.collectTotal} rows={d.toCollect} />
          <Divider />
          <Section title="To receive" total={d.receiveTotal} rows={d.toReceive} />
        </Stack>
      )}
    </Panel>
  );
}

/** Front desk: patients to call back for a new sample, straight on the dashboard. */
export function SampleAlerts({ widget }) {
  return (
    <GlowCard p="lg" style={{ borderColor: 'color-mix(in oklab, var(--accent) 35%, var(--border))' }}>
      <Group justify="space-between" mb="md">
        <Group gap={8}>
          <IconPhoneCall size={18} color="var(--accent)" />
          <Text fw={600}>{widget.title}</Text>
          <Text size="sm" c="var(--text-muted)">· {widget.data.open} open</Text>
        </Group>
        <Link href="/reception/samples" style={linkStyle}>Inbox</Link>
      </Group>
      <RedrawAlerts items={widget.data.items} compact />
    </GlowCard>
  );
}
