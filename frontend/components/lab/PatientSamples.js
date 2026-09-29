'use client';

import { Box, Group, Stack, Text } from '@mantine/core';
import { IconHeartHandshake } from '@tabler/icons-react';
import { journeyFor, JOURNEY_STAGES, plainStatus } from '@/lib/samples';
import { formatShort } from '@/lib/format';
import { SampleJourney } from '@/components/ui/SampleJourney';
import { TubeChip } from '@/components/ui/TubeChip';

/**
 * A patient's samples for one order, each as the journey "subway line" (Design.md §5.1). Only
 * patient-relevant stages show; no staff names. A rejected sample gets a kind message instead of a
 * reason — the new sample appears as its own line, starting again from Ordered.
 */
export function PatientSamples({ samples }) {
  if (!samples || samples.length === 0) return null;
  return (
    <Stack gap="md">
      <Text size="xs" fw={700} tt="uppercase" c="var(--text-subtle)" style={{ letterSpacing: '0.06em' }}>Your samples</Text>
      {samples.map((sample) => {
        const rejected = sample.status === 'REJECTED';
        const cancelled = sample.status === 'CANCELLED';
        if (cancelled) return null;
        const { current, times } = journeyFor(sample);
        return (
          <Box key={sample.id} p="md" style={{ borderRadius: 'var(--radius-md)', border: '1px solid var(--border)', background: 'var(--surface-alt)', opacity: rejected ? 0.85 : 1 }}>
            <Group justify="space-between" wrap="wrap" gap="xs" mb="sm">
              <Group gap={8}>
                <TubeChip tube={sample.requiredTubeType} />
                <Text size="sm" fw={600} className="mono">{sample.sampleCode}</Text>
              </Group>
              <Text size="xs" c="var(--text-muted)">{sample.tests.map((t) => t.name).join(', ')}</Text>
            </Group>
            {rejected ? (
              <Group gap="sm" wrap="nowrap" align="flex-start" p="sm" style={{ borderRadius: 'var(--radius-sm)', background: 'var(--info-soft)' }}>
                <IconHeartHandshake size={20} color="var(--info)" style={{ flex: 'none', marginTop: 2 }} />
                <div>
                  <Text size="sm" fw={600}>We need a new sample</Text>
                  <Text size="sm" c="var(--text-muted)">
                    Something about this one means we can’t test it reliably. The front desk will contact you to come in again
                    {sample.redrawSampleCode ? ` — it will be ${sample.redrawSampleCode}.` : '.'}
                  </Text>
                </div>
              </Group>
            ) : (
              <SampleJourney
                label={`Sample ${sample.sampleCode}`}
                stages={JOURNEY_STAGES}
                current={current}
                times={times.map((t) => (t ? formatShort(t) : null))}
                currentNote={plainStatus(sample.status)}
              />
            )}
          </Box>
        );
      })}
    </Stack>
  );
}
