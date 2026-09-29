'use client';

import { Group, Stack, Text } from '@mantine/core';
import { IconCircleCheck, IconFileText, IconMicroscope, IconSend } from '@tabler/icons-react';
import Link from 'next/link';
import { RETURN_REASON_LABEL } from '@/lib/results';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { Panel } from '@/components/ui/Panel';
import { TestingRow } from '@/components/lab/SampleBits';
import { VerificationRow } from '@/components/pathology/QueueViews';
import { ReportRowItem } from '@/components/reports/ReportLists';

const linkStyle = { fontSize: 13, fontWeight: 600, color: 'var(--accent)' };

/** Pathologist: the results waiting for sign-off, critical first. */
export function VerificationQueue({ widget }) {
  const { results, total } = widget.data;
  return (
    <Panel
      title={widget.title}
      subtitle={total ? `${total} waiting · critical first` : 'Nothing waiting'}
      right={<Link href="/pathology/queue" style={linkStyle}>Open queue</Link>}
    >
      {results.length === 0 ? (
        <EmptyState icon={IconCircleCheck} title="Nothing waiting" compact>Results the lab enters appear here for your sign-off.</EmptyState>
      ) : (
        <Stack gap={2}>
          {results.map((r) => <VerificationRow key={r.sampleId} row={r} href={`/pathology/review/${r.sampleId}`} />)}
        </Stack>
      )}
    </Panel>
  );
}

/** Lab: samples ready to test, retests pinned first with the pathologist's reason. */
export function TestQueue({ widget }) {
  const { samples, total } = widget.data;
  return (
    <Panel
      title={widget.title}
      subtitle={total ? `${total} at the lab · retests first` : 'Nothing to test'}
      right={<Link href="/lab/samples" style={linkStyle}>Sample bench</Link>}
    >
      {samples.length === 0 ? (
        <EmptyState icon={IconMicroscope} title="Nothing to test" compact>Samples that pass the receipt check appear here.</EmptyState>
      ) : (
        <Stack gap={2}>
          {samples.map((s) => <TestingRow key={s.id} row={s} href={`/lab/samples/${s.id}`} reasonLabel={RETURN_REASON_LABEL} />)}
        </Stack>
      )}
    </Panel>
  );
}

/** Lab: verified reports that have not been sent yet. */
export function DispatchQueue({ widget }) {
  const { reports, total } = widget.data;
  return (
    <Panel
      title={widget.title}
      subtitle={total ? `${total} verified, not sent` : 'All sent'}
      right={<Link href="/lab/reports" style={linkStyle}>All</Link>}
    >
      {reports.length === 0 ? (
        <EmptyState icon={IconSend} title="Nothing to send" compact>Verified reports appear here until they are sent.</EmptyState>
      ) : (
        <Stack gap={2}>
          {reports.map((r) => <ReportRowItem key={r.sampleId} row={r} href={`/lab/reports/${r.sampleId}`} showPatient />)}
        </Stack>
      )}
    </Panel>
  );
}

/** Patient: reports sent to them, so a new result is one tap away. */
export function MyReports({ widget }) {
  return (
    <GlowCard p="lg" style={{ borderColor: 'color-mix(in oklab, var(--accent) 30%, var(--border))' }}>
      <Group justify="space-between" mb="sm">
        <Group gap={8}>
          <IconFileText size={18} color="var(--accent)" />
          <Text fw={600}>{widget.title}</Text>
        </Group>
        <Link href="/patient/reports" style={linkStyle}>All reports</Link>
      </Group>
      <Stack gap={2}>
        {widget.data.map((r) => <ReportRowItem key={r.sampleId} row={r} href={`/patient/reports/${r.sampleId}`} />)}
      </Stack>
    </GlowCard>
  );
}

/** Doctor: verified reports for tests they ordered. */
export function ReportsReady({ widget }) {
  return (
    <Panel title={widget.title} subtitle="Verified by a pathologist" right={<Link href="/doctor/reports" style={linkStyle}>All reports</Link>}>
      <Stack gap={2}>
        {widget.data.map((r) => <ReportRowItem key={r.sampleId} row={r} href={`/doctor/reports/${r.sampleId}`} showPatient />)}
      </Stack>
    </Panel>
  );
}
