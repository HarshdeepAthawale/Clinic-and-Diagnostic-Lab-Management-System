'use client';

import { Alert, Anchor, Box, Button, Group, Radio, Skeleton, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertOctagon, IconAlertCircle, IconCheck, IconDownload, IconFileText, IconSend, IconShieldCheck } from '@tabler/icons-react';
import Link from 'next/link';
import { useState } from 'react';
import { verifyPath } from '@/lib/verify';
import { CHANNEL_LABEL, DISPATCH_CHANNELS, reportPdfUrl, useDispatchReport, useReport } from '@/lib/results';
import { friendlyMessage } from '@/lib/errors';
import { ageGender, formatDate, formatDateTime } from '@/lib/format';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { Panel } from '@/components/ui/Panel';
import { Reveal } from '@/components/ui/Reveal';
import { ResultTable } from '@/components/results/ResultBits';
import { TubeChip } from '@/components/ui/TubeChip';

function PdfButtons({ sampleId }) {
  return (
    <Group gap="sm">
      <Button component="a" href={reportPdfUrl(sampleId)} target="_blank" rel="noopener" variant="default" leftSection={<IconFileText size={16} />}>
        View PDF
      </Button>
      <Button component="a" href={reportPdfUrl(sampleId, true)} variant="default" leftSection={<IconDownload size={16} />}>
        Download
      </Button>
    </Group>
  );
}

/** The pathologist's sign-off as it appears on the report. */
function VerificationStamp({ report }) {
  const v = report.verifier;
  return (
    <Box p="md" style={{ borderRadius: 'var(--radius-md)', border: '1.5px solid color-mix(in oklab, var(--success) 45%, transparent)', background: 'var(--success-soft)' }}>
      <Group gap={8} mb={4}>
        <IconShieldCheck size={18} color="var(--success)" />
        <Text size="xs" fw={700} tt="uppercase" c="var(--success)" style={{ letterSpacing: '0.08em' }}>Digitally verified</Text>
      </Group>
      <Text fw={600}>{v.name}</Text>
      {v.qualification && <Text size="sm" c="var(--text-muted)">{v.qualification}</Text>}
      {v.registrationNumber && <Text size="xs" c="var(--text-muted)" className="mono">Reg. no. {v.registrationNumber}</Text>}
      <Text size="xs" c="var(--text-muted)" mt={4}>Verified {formatDateTime(report.verifiedAt)}</Text>
      {report.verificationCode && (
        <Anchor component={Link} href={verifyPath(report.verificationCode)} target="_blank" size="xs" fw={600} c="var(--success)" mt={6} style={{ display: 'inline-block' }}>
          See what a scan of the printed QR code shows
        </Anchor>
      )}
    </Box>
  );
}

/** The report as a reader (not a PDF): each test's values with the range bar, flag and unit. */
export function ReportBody({ report }) {
  const groups = report.tests.map((t) => ({ key: t.testCode, title: t.testName, code: t.testCode, values: t.values }));
  return (
    <Stack gap="lg">
      {report.critical && (
        <Alert color="red" variant="light" radius="md" icon={<IconAlertOctagon size={18} />} title="Critical value reported">
          One or more values are at a critical limit. Please act on these results promptly.
        </Alert>
      )}
      <ResultTable groups={groups} />
      <VerificationStamp report={report} />
    </Stack>
  );
}

/** Choose how to send the report to the patient; SMS is listed but off until a provider is chosen. */
function DispatchPanel({ report }) {
  const dispatch = useDispatchReport(report.sampleId);
  const [channel, setChannel] = useState('EMAIL');

  if (report.dispatchedAt) {
    return (
      <Panel title="Sent to the patient" subtitle={`${CHANNEL_LABEL[report.dispatchedChannel] ?? report.dispatchedChannel} · ${formatDateTime(report.dispatchedAt)}`}>
        <Group gap={8}>
          <IconCheck size={18} color="var(--success)" />
          <Text size="sm">
            {report.receiptConfirmedAt
              ? `The patient opened it on ${formatDateTime(report.receiptConfirmedAt)}.`
              : 'Not opened by the patient yet. Receipt is confirmed the first time they open it.'}
          </Text>
        </Group>
      </Panel>
    );
  }

  return (
    <Panel title="Send to the patient" subtitle="They can open the report once you send it">
      <Stack gap="md">
        <Radio.Group value={channel} onChange={setChannel}>
          <Stack gap="xs">
            {DISPATCH_CHANNELS.map((c) => (
              <Radio key={c.value} value={c.value} label={c.label} description={c.hint} disabled={c.disabled} />
            ))}
          </Stack>
        </Radio.Group>
        {dispatch.error && <Alert color="red" variant="light" radius="md" icon={<IconAlertCircle size={16} />}>{friendlyMessage(dispatch.error)}</Alert>}
        <Button
          onClick={() =>
            dispatch.mutate(channel, {
              onSuccess: () =>
                notifications.show({ title: 'Report sent', message: `${report.patient.fullName} can now open ${report.sampleCode}.`, color: 'teal', radius: 'lg', icon: <IconSend size={18} /> }),
            })
          }
          loading={dispatch.isPending}
          leftSection={<IconSend size={16} />}
        >
          Send report
        </Button>
      </Stack>
    </Panel>
  );
}

/**
 * One report page. `audience` decides what surrounds it: the lab gets the send panel, patients and
 * doctors just the report and its PDF.
 */
export function ReportPage({ sampleId, audience, back }) {
  const query = useReport(sampleId);
  if (query.isPending) return <Stack gap="lg"><Skeleton height={40} width={260} radius="md" /><Skeleton height={360} radius="xl" /></Stack>;
  if (query.isError) {
    return (
      <Alert color="red" variant="light" radius="lg" icon={<IconAlertCircle size={18} />} title="Couldn’t open this report">
        {query.error?.status === 404 ? 'This report doesn’t exist, or isn’t available to you yet.' : friendlyMessage(query.error)}
      </Alert>
    );
  }
  const report = query.data;
  const p = report.patient;
  return (
    <Stack gap="xl">
      <PageTitle title="Lab report" subtitle={report.tests.map((t) => t.testName).join(', ')} back={back} actions={<PdfButtons sampleId={report.sampleId} />} />
      <Reveal>
        <GlowCard p="xl">
          <Group justify="space-between" align="flex-start" wrap="wrap" gap="md" mb="lg">
            <div>
              <Text fz={24} fw={600} style={{ letterSpacing: '-0.02em' }}>{p.fullName}</Text>
              <Text size="sm" c="var(--text-muted)" className="mono">{p.patientCode} · {ageGender(p.age, p.gender)}</Text>
            </div>
            <Stack gap={4} align="flex-end">
              <Group gap={8}>
                <TubeChip tube={report.tubeType} />
                <Text size="sm" className="mono" fw={600}>{report.sampleCode}</Text>
              </Group>
              <Text size="xs" c="var(--text-muted)">
                Collected {report.collectedAt ? formatDate(report.collectedAt) : '—'} · reported {formatDate(report.verifiedAt)}
              </Text>
              {report.orderingDoctor && <Text size="xs" c="var(--text-muted)">Requested by {report.orderingDoctor}</Text>}
              {report.attemptNumber > 1 && <Text size="xs" c="var(--text-subtle)">After {report.attemptNumber - 1} retest{report.attemptNumber === 2 ? '' : 's'}</Text>}
            </Stack>
          </Group>
          <ReportBody report={report} />
        </GlowCard>
      </Reveal>
      {audience === 'lab' && <Reveal delay={0.05}><DispatchPanel report={report} /></Reveal>}
    </Stack>
  );
}
