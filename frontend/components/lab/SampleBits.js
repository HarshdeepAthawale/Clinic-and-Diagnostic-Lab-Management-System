import { Badge, Group, Stack, Text } from '@mantine/core';
import { IconAlertTriangle, IconArrowBackUp } from '@tabler/icons-react';
import { SAMPLE_STATUS_LABEL } from '@/lib/samples';
import { formatDateTime, formatRelative } from '@/lib/format';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { TubeChip } from '@/components/ui/TubeChip';
import { ListRow } from '@/components/dashboard/widgets/ListRow';
import { UrgentBadge } from './OrderLines';

/** Maps a sample status onto the app's one status vocabulary. */
const BADGE = {
  ORDERED: 'to-collect',
  COLLECTED: 'to-receive',
  RECEIVED_AT_LAB: 'checked-in',
  IN_TESTING: 'in-testing',
  RESULT_ENTERED: 'pending',
  VERIFIED: 'verified',
  REPORT_GENERATED: 'done',
  DISPATCHED: 'done',
  REJECTED: 'rejected',
  CANCELLED: 'no-show',
};

export function SampleStatusBadge({ status }) {
  return <StatusBadge status={BADGE[status] ?? 'pending'} label={SAMPLE_STATUS_LABEL[status] ?? status} />;
}

export function MismatchFlag() {
  return (
    <Badge size="sm" radius="sm" leftSection={<IconAlertTriangle size={12} />} styles={{ root: { textTransform: 'none', background: 'var(--warning-soft)', color: 'var(--warning)', fontWeight: 700 } }}>
      Wrong tube
    </Badge>
  );
}

export function RedrawBadge() {
  return (
    <Badge size="sm" radius="sm" leftSection={<IconArrowBackUp size={12} />} styles={{ root: { textTransform: 'none', background: 'var(--info-soft)', color: 'var(--info)', fontWeight: 700 } }}>
      Redraw
    </Badge>
  );
}

/** A row in the lab's sample lists: patient, code and tests, with the flags that change what to do first. */
export function SampleRow({ sample, href }) {
  return (
    <ListRow
      href={href}
      leading={
        <div style={{ width: 4, height: 36, flex: 'none', borderRadius: 4, background: sample.priority === 'URGENT' ? 'var(--critical)' : sample.redraw ? 'var(--info)' : 'var(--border-strong)' }} />
      }
      title={sample.patientName}
      subtitle={`${sample.sampleCode} · ${sample.testNames.join(', ')} · ${formatRelative(sample.collectedAt ?? sample.createdAt)}`}
      right={
        <Group gap={6} wrap="nowrap">
          {sample.priority === 'URGENT' && <UrgentBadge />}
          {sample.redraw && <RedrawBadge />}
          {sample.tubeMismatch && <MismatchFlag />}
          <TubeChip tube={sample.requiredTubeType} />
        </Group>
      }
    />
  );
}

/** The chain of custody: every step with who did it and when (staff only — patients don't get names). */
export function CustodyLog({ events }) {
  return (
    <Stack gap={0}>
      {events.map((e, i) => (
        <Group key={`${e.status}-${e.occurredAt}-${i}`} wrap="nowrap" align="flex-start" gap="sm" py={10} style={{ borderTop: i ? '1px solid var(--border)' : undefined }}>
          <div style={{ width: 8, height: 8, marginTop: 6, flex: 'none', borderRadius: 999, background: e.status === 'REJECTED' ? 'var(--critical)' : 'var(--ink)' }} />
          <div style={{ minWidth: 0 }}>
            <Text size="sm" fw={600}>{SAMPLE_STATUS_LABEL[e.status] ?? e.status}</Text>
            <Text size="xs" c="var(--text-muted)">
              {formatDateTime(e.occurredAt)}{e.actorName ? ` · ${e.actorName}` : ''}
            </Text>
            {e.detail && <Text size="xs" c="var(--text-subtle)">{e.detail}</Text>}
          </div>
        </Group>
      ))}
    </Stack>
  );
}
