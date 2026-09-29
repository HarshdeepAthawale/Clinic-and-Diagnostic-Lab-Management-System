'use client';

import { Alert, Box, Button, Grid, Group, Skeleton, Stack, Text } from '@mantine/core';
import { IconAlertCircle, IconArrowRight } from '@tabler/icons-react';
import Link from 'next/link';
import { REJECTION_REASON_LABEL, useSample } from '@/lib/samples';
import { friendlyMessage } from '@/lib/errors';
import { ageGender, formatDateTime } from '@/lib/format';
import { PageTitle } from '@/components/ui/PageTitle';
import { Panel } from '@/components/ui/Panel';
import { Reveal } from '@/components/ui/Reveal';
import { TUBES, TubeChip } from '@/components/ui/TubeChip';
import { CollectForm } from './CollectForm';
import { OrderCode, UrgentBadge } from './OrderLines';
import { TestingStep } from '@/components/results/TestingPanels';
import { ReceiptCheck } from './ReceiptCheck';
import { CustodyLog, MismatchFlag, RedrawBadge, SampleStatusBadge } from './SampleBits';
import { SampleLabel } from './SampleLabel';

/** What to do with a sample now: the one primary panel for its state, or a plain note when it's beyond the bench. */
function NextStep({ sample }) {
  switch (sample.status) {
    case 'ORDERED':
      return <CollectForm sample={sample} />;
    case 'COLLECTED':
      return <ReceiptCheck sample={sample} />;
    case 'REJECTED': {
      const r = sample.rejection;
      return (
        <Panel title="Rejected" subtitle="Kept as a permanent record">
          <Stack gap="sm">
            {r && (
              <Alert color="red" variant="light" radius="md" icon={<IconAlertCircle size={18} />}
                title={`${REJECTION_REASON_LABEL[r.reason] ?? r.reason} · at ${r.stage === 'RECEIVED_AT_LAB' ? 'the receipt check' : 'testing'}`}>
                {r.note && <Text size="sm">{r.note}</Text>}
                <Text size="xs" c="var(--text-muted)">{formatDateTime(r.flaggedAt)}{r.flaggedBy ? ` · ${r.flaggedBy}` : ''}</Text>
              </Alert>
            )}
            {sample.redrawSampleId && (
              <Button component={Link} href={`/lab/samples/${sample.redrawSampleId}`} rightSection={<IconArrowRight size={16} />}>
                Go to the redraw {sample.redrawSampleCode}
              </Button>
            )}
          </Stack>
        </Panel>
      );
    }
    case 'CANCELLED':
      return (
        <Panel title="Cancelled" subtitle="Every test on this sample was taken off the order before it was drawn" />
      );
    default:
      return <TestingStep sample={sample} />;
  }
}

/** One sample for the technician: patient and tests, the next step, the label to print and the custody log. */
export function SampleWorkspace({ id }) {
  const query = useSample(id);
  if (query.isPending) return <Stack gap="lg"><Skeleton height={40} width={280} radius="md" /><Skeleton height={360} radius="xl" /></Stack>;
  if (query.isError) {
    return (
      <Alert color="red" variant="light" radius="lg" icon={<IconAlertCircle size={18} />} title="Couldn’t open this sample">
        {query.error?.status === 404 ? 'No sample with that code.' : friendlyMessage(query.error)}
      </Alert>
    );
  }
  const sample = query.data;
  const p = sample.patient;
  return (
    <Stack gap="xl">
      <PageTitle
        title={<span className="mono">{sample.sampleCode}</span>}
        subtitle={`${p.fullName} · ${ageGender(p.age, p.gender)}`}
        back={{ href: '/lab/samples', label: 'All samples' }}
        actions={
          <Group gap={6}>
            {sample.priority === 'URGENT' && <UrgentBadge />}
            {sample.redrawOfSampleCode && <RedrawBadge />}
            {sample.tubeMismatch && <MismatchFlag />}
            <SampleStatusBadge status={sample.status} />
          </Group>
        }
      />
      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: 7 }}>
          <Stack gap="lg">
            <Reveal><NextStep sample={sample} /></Reveal>
            <Reveal delay={0.05}>
              <Panel title="Tests on this sample" subtitle={`${TUBES[sample.requiredTubeType]?.label ?? sample.requiredTubeType} tube`}>
                <Stack gap={0}>
                  {sample.tests.map((t) => (
                    <Group key={t.itemId} justify="space-between" py={8} style={{ borderTop: '1px solid var(--border)' }}>
                      <Text size="sm" fw={600}>{t.name}</Text>
                      <Text size="xs" className="mono" c="var(--text-subtle)">{t.code}</Text>
                    </Group>
                  ))}
                </Stack>
                <Group gap={8} mt="sm">
                  <OrderCode code={sample.orderCode} />
                  <Link href={`/lab/orders/${sample.labOrderId}`} style={{ fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>View the order</Link>
                </Group>
              </Panel>
            </Reveal>
          </Stack>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 5 }}>
          <Stack gap="lg">
            <Reveal delay={0.03}>
              <Panel title="Label" subtitle="Stick it on the tube before the draw">
                <SampleLabel sample={sample} />
              </Panel>
            </Reveal>
            <Reveal delay={0.08}>
              <Panel title="Chain of custody" subtitle="Every step, who did it, when">
                <CustodyLog events={sample.events} />
                {sample.redrawOfSampleCode && (
                  <Box mt="sm"><Text size="xs" c="var(--text-muted)">Redraw of {sample.redrawOfSampleCode}</Text></Box>
                )}
              </Panel>
            </Reveal>
          </Stack>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
