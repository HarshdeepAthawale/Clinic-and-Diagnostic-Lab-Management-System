'use client';

import { Badge, Button, Group, Stack, Text } from '@mantine/core';
import { IconArrowRight, IconFileText } from '@tabler/icons-react';
import Link from 'next/link';
import { prescriptionPdfUrl } from '@/lib/consultations';
import { formatDate, formatTime } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { Panel } from '@/components/ui/Panel';
import { ListRow } from './ListRow';

/** Doctor: the consultation still in progress, so it's one click to get back to it. */
export function OpenConsultation({ widget }) {
  const c = widget.data;
  return (
    <GlowCard p="lg" style={{ borderColor: 'color-mix(in oklab, var(--accent) 35%, var(--border))' }}>
      <Group justify="space-between" wrap="wrap" gap="md">
        <div>
          <Group gap={8} mb={4}>
            <span className="live-dot" style={{ '--dot': 'var(--accent)' }} />
            <Text size="xs" fw={700} tt="uppercase" c="var(--accent)" style={{ letterSpacing: '0.06em' }}>
              In consultation{c.queueToken ? ` · ${c.queueToken}` : ''}
            </Text>
          </Group>
          <Text fw={600} size="lg">{c.patientName}</Text>
          <Text size="sm" c="var(--text-muted)">
            <span className="mono">{c.patientCode}</span> · started {formatTime(c.startedAt)}
          </Text>
        </div>
        <Button component={Link} href={`/doctor/consultations/${c.consultationId}`} rightSection={<IconArrowRight size={16} />}>
          Resume consultation
        </Button>
      </Group>
    </GlowCard>
  );
}

/** Patient: latest prescriptions with a one-tap PDF. */
export function RecentPrescriptions({ widget }) {
  return (
    <Panel
      title={widget.title}
      right={<Link href="/patient/prescriptions" style={{ fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>All</Link>}
    >
      {widget.data.length === 0 ? (
        <EmptyState icon={IconFileText} title="No prescriptions yet" compact>
          Prescriptions from your visits appear here, ready to download.
        </EmptyState>
      ) : (
        <Stack gap={2}>
          {widget.data.map((rx) => (
            <ListRow
              key={rx.id}
              href={`/patient/prescriptions/${rx.id}`}
              title={rx.diagnosis}
              subtitle={`${rx.doctorName} · ${formatDate(rx.issuedAt)}`}
              right={
                <Group gap="xs" wrap="nowrap">
                  <Badge size="sm" radius="sm" variant="outline" color="dark" styles={{ root: { textTransform: 'none', fontFamily: 'var(--font-mono)' } }}>
                    {rx.prescriptionCode}
                  </Badge>
                  <Button component="a" href={prescriptionPdfUrl(rx.id, true)} size="xs" variant="default" onClick={(e) => e.stopPropagation()}>
                    PDF
                  </Button>
                </Group>
              }
            />
          ))}
        </Stack>
      )}
    </Panel>
  );
}
