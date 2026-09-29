'use client';

import { Badge, Group, Pagination, Skeleton, Stack, Text } from '@mantine/core';
import { IconAlertOctagon, IconFileText } from '@tabler/icons-react';
import { useState } from 'react';
import { CHANNEL_LABEL, useMyReports, useOrderedReports, useReportsToDispatch } from '@/lib/results';
import { friendlyMessage } from '@/lib/errors';
import { formatDate, formatRelative } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { Panel } from '@/components/ui/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { ListRow } from '@/components/dashboard/widgets/ListRow';

const pill = (background, color) => ({ root: { textTransform: 'none', background, color, fontWeight: 700 } });

/** A report in a list: the tests, when, and what to notice (critical, out of range, opened or not). */
export function ReportRowItem({ row, href, showPatient = false, showState = false }) {
  return (
    <ListRow
      href={href}
      leading={
        <div style={{ width: 4, height: 36, flex: 'none', borderRadius: 4, background: row.critical ? 'var(--critical)' : row.abnormal ? 'var(--warning)' : 'var(--border-strong)' }} />
      }
      title={row.testNames.join(', ')}
      subtitle={[
        showPatient ? row.patientName : null,
        row.sampleCode,
        row.dispatchedAt ? `sent ${formatDate(row.dispatchedAt)}` : `verified ${formatRelative(row.verifiedAt)}`,
      ].filter(Boolean).join(' · ')}
      right={
        <Group gap={6} wrap="nowrap">
          {row.critical && (
            <Badge size="sm" radius="sm" leftSection={<IconAlertOctagon size={12} />} styles={pill('var(--critical-soft)', 'var(--critical)')}>Critical</Badge>
          )}
          {!row.critical && row.abnormal && <Badge size="sm" radius="sm" styles={pill('var(--warning-soft)', 'var(--warning)')}>Out of range</Badge>}
          {showState && (
            row.dispatchedAt
              ? <StatusBadge status="verified" label={row.receiptConfirmedAt ? 'Opened' : CHANNEL_LABEL[row.dispatchedChannel] ?? 'Sent'} />
              : <StatusBadge status="pending" label="Not sent" />
          )}
        </Group>
      }
    />
  );
}

function Loading() {
  return <Stack gap="xs">{[0, 1, 2].map((i) => <Skeleton key={i} height={56} radius="md" />)}</Stack>;
}

export function PatientReportsView() {
  const reports = useMyReports();
  return (
    <Stack gap="xl">
      <PageTitle title="Reports" subtitle="Your lab reports, checked by a pathologist. Open one to read it or download the PDF." />
      <Panel title={reports.data ? `${reports.data.length} report${reports.data.length === 1 ? '' : 's'}` : 'Reports'}>
        {reports.isPending ? (
          <Loading />
        ) : reports.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(reports.error)}</Text>
        ) : reports.data.length === 0 ? (
          <EmptyState icon={IconFileText} title="No reports yet">
            When your tests are done and checked, the report appears here. You can follow each sample under Lab tests.
          </EmptyState>
        ) : (
          <Stack gap={2}>
            {reports.data.map((r) => <ReportRowItem key={r.sampleId} row={r} href={`/patient/reports/${r.sampleId}`} />)}
          </Stack>
        )}
      </Panel>
    </Stack>
  );
}

export function DoctorReportsView() {
  const [page, setPage] = useState(1);
  const reports = useOrderedReports(page - 1);
  const totalPages = reports.data ? Math.ceil(reports.data.totalElements / reports.data.size) : 0;
  return (
    <Stack gap="xl">
      <PageTitle title="Lab reports" subtitle="Verified reports for tests you ordered, newest first." />
      <GlowCard p="lg">
        {reports.isPending ? (
          <Loading />
        ) : reports.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(reports.error)}</Text>
        ) : reports.data.content.length === 0 ? (
          <EmptyState icon={IconFileText} title="No reports yet">Reports appear here as soon as a pathologist verifies them.</EmptyState>
        ) : (
          <Stack gap={2}>
            {reports.data.content.map((r) => <ReportRowItem key={r.sampleId} row={r} href={`/doctor/reports/${r.sampleId}`} showPatient showState />)}
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

/** The lab's list of verified reports that still have to be sent to their patients. */
export function DispatchListView() {
  const [page, setPage] = useState(1);
  const reports = useReportsToDispatch(page - 1);
  const totalPages = reports.data ? Math.ceil(reports.data.totalElements / reports.data.size) : 0;
  return (
    <Stack gap="xl">
      <PageTitle title="Reports to send" subtitle="Verified reports waiting to reach their patients. Critical ones first." />
      <GlowCard p="lg">
        {reports.isPending ? (
          <Loading />
        ) : reports.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(reports.error)}</Text>
        ) : reports.data.content.length === 0 ? (
          <EmptyState icon={IconFileText} title="Nothing to send">Every verified report has been sent.</EmptyState>
        ) : (
          <Stack gap={2}>
            {reports.data.content.map((r) => <ReportRowItem key={r.sampleId} row={r} href={`/lab/reports/${r.sampleId}`} showPatient />)}
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
