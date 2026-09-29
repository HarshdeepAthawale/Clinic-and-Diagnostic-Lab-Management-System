'use client';

import { Group, Skeleton, Stack, Text } from '@mantine/core';
import { IconTestPipe } from '@tabler/icons-react';
import Link from 'next/link';
import { useOrderSamples } from '@/lib/samples';
import { friendlyMessage } from '@/lib/errors';
import { formatRelative } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { Panel } from '@/components/ui/Panel';
import { TubeChip } from '@/components/ui/TubeChip';
import { MismatchFlag, RedrawBadge, SampleStatusBadge } from './SampleBits';

/**
 * The samples of one order and where each is. The lab links each to its bench page; doctors see the
 * status only (they don't work on samples).
 */
export function OrderSamples({ orderId, labView = false }) {
  const samples = useOrderSamples(orderId);
  return (
    <Panel title="Samples" subtitle="One per tube; tests that share a tube share a sample">
      {samples.isPending ? (
        <Skeleton height={52} radius="md" />
      ) : samples.isError ? (
        <Text size="sm" c="var(--critical)">{friendlyMessage(samples.error)}</Text>
      ) : samples.data.length === 0 ? (
        <EmptyState icon={IconTestPipe} title="No samples" compact>Samples are created when tests are ordered.</EmptyState>
      ) : (
        <Stack gap={0}>
          {samples.data.map((s) => {
            const body = (
              <Group justify="space-between" wrap="nowrap" py={10} style={{ borderTop: '1px solid var(--border)' }}>
                <div style={{ minWidth: 0 }}>
                  <Text size="sm" fw={600} className="mono">{s.sampleCode}</Text>
                  <Text size="xs" c="var(--text-muted)" truncate>
                    {s.tests.map((t) => t.name).join(', ')}
                    {s.collectedAt ? ` · collected ${formatRelative(s.collectedAt)}` : ''}
                  </Text>
                </div>
                <Group gap={6} wrap="nowrap">
                  {s.redrawOfSampleCode && <RedrawBadge />}
                  {s.tubeMismatch && <MismatchFlag />}
                  <TubeChip tube={s.requiredTubeType} />
                  <SampleStatusBadge status={s.status} />
                </Group>
              </Group>
            );
            return labView ? (
              <Link key={s.id} href={`/lab/samples/${s.id}`} style={{ display: 'block' }}>{body}</Link>
            ) : (
              <div key={s.id}>{body}</div>
            );
          })}
        </Stack>
      )}
    </Panel>
  );
}
