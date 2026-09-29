'use client';

import { Badge, Button, Group, Pagination, Skeleton, Stack, Text } from '@mantine/core';
import { IconAlertOctagon, IconCircleCheck, IconHistory, IconPlayerPlay, IconRefresh } from '@tabler/icons-react';
import Link from 'next/link';
import { useState } from 'react';
import { usePendingVerification, useVerifiedByMe } from '@/lib/results';
import { friendlyMessage } from '@/lib/errors';
import { formatRelative, formatShort } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { ListRow } from '@/components/dashboard/widgets/ListRow';

export function CriticalBadge() {
  return (
    <Badge size="sm" radius="sm" leftSection={<IconAlertOctagon size={12} />} styles={{ root: { textTransform: 'none', background: 'var(--critical-soft)', color: 'var(--critical)', fontWeight: 700 } }}>
      Critical
    </Badge>
  );
}

/** A result in the pathologist's queue or history: the patient, the tests, and what stands out. */
export function VerificationRow({ row, href, showVerified = false }) {
  return (
    <ListRow
      href={href}
      leading={
        <div style={{ width: 4, height: 36, flex: 'none', borderRadius: 4, background: row.critical ? 'var(--critical)' : row.abnormalCount > 0 ? 'var(--warning)' : 'var(--border-strong)' }} />
      }
      title={row.patientName}
      subtitle={`${row.sampleCode} · ${row.testNames.join(', ')} · ${showVerified ? `verified ${formatShort(row.verifiedAt)}` : `entered ${formatRelative(row.enteredAt)}`}`}
      right={
        <Group gap={6} wrap="nowrap">
          {row.critical && <CriticalBadge />}
          {!row.critical && row.abnormalCount > 0 && (
            <Badge size="sm" radius="sm" styles={{ root: { textTransform: 'none', background: 'var(--warning-soft)', color: 'var(--warning)', fontWeight: 700 } }}>
              {row.abnormalCount} out of range
            </Badge>
          )}
          {row.attemptNumber > 1 && (
            <Badge size="sm" radius="sm" leftSection={<IconRefresh size={12} />} styles={{ root: { textTransform: 'none', background: 'var(--surface-2)', color: 'var(--text-muted)', fontWeight: 700 } }}>
              Retest {row.attemptNumber - 1}
            </Badge>
          )}
        </Group>
      }
    />
  );
}

/** The verification queue: critical first, then anything out of range, then the longest waiting. */
export function VerificationQueueView() {
  const queue = usePendingVerification(0, 100);
  const rows = queue.data?.content ?? [];
  return (
    <Stack gap="xl">
      <PageTitle
        title="Verification queue"
        subtitle="Results waiting for your sign-off. Critical values come first."
        actions={
          rows.length > 0 && (
            <Button component={Link} href={`/pathology/review/${rows[0].sampleId}`} leftSection={<IconPlayerPlay size={16} />}>
              Start reviewing
            </Button>
          )
        }
      />
      <GlowCard p="lg">
        {queue.isPending ? (
          <Stack gap="xs">{[0, 1, 2].map((i) => <Skeleton key={i} height={56} radius="md" />)}</Stack>
        ) : queue.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(queue.error)}</Text>
        ) : rows.length === 0 ? (
          <EmptyState icon={IconCircleCheck} title="Nothing waiting">
            Results the lab enters appear here for your sign-off.
          </EmptyState>
        ) : (
          <Stack gap={2}>
            {rows.map((r) => <VerificationRow key={r.sampleId} row={r} href={`/pathology/review/${r.sampleId}`} />)}
          </Stack>
        )}
      </GlowCard>
    </Stack>
  );
}

/** What this pathologist has signed off, newest first. */
export function VerifiedHistoryView() {
  const [page, setPage] = useState(1);
  const history = useVerifiedByMe(page - 1);
  const totalPages = history.data ? Math.ceil(history.data.totalElements / history.data.size) : 0;
  return (
    <Stack gap="xl">
      <PageTitle title="My verifications" subtitle="Results you have signed off. Each one has a report." />
      <GlowCard p="lg">
        {history.isPending ? (
          <Stack gap="xs">{[0, 1, 2].map((i) => <Skeleton key={i} height={56} radius="md" />)}</Stack>
        ) : history.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(history.error)}</Text>
        ) : history.data.content.length === 0 ? (
          <EmptyState icon={IconHistory} title="No verifications yet">Results you verify are listed here.</EmptyState>
        ) : (
          <Stack gap={2}>
            {history.data.content.map((r) => <VerificationRow key={r.sampleId} row={r} showVerified />)}
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
