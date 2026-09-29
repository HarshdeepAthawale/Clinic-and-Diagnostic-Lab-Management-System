'use client';

import { Box, Group, Loader, Stack, Text, ThemeIcon } from '@mantine/core';
import { IconCircleCheck, IconShieldCheck, IconShieldOff } from '@tabler/icons-react';
import Link from 'next/link';
import { formatDateTime } from '@/lib/format';
import { isReportCode, useReportCheck } from '@/lib/verify';
import { BrandMark } from '@/components/ui/BrandMark';
import { GlowCard } from '@/components/ui/GlowCard';

function Frame({ children }) {
  return (
    <Box component="main" id="main" mih="100dvh" p={{ base: 'md', sm: 'xl' }} style={{ display: 'grid', placeItems: 'center', background: 'var(--bg)' }}>
      <Stack gap="lg" w="100%" maw={460}>
        <Group justify="space-between">
          <BrandMark subtitle="Report check" />
          <Link href="/login" style={{ fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>Sign in</Link>
        </Group>
        {children}
        <Text size="xs" c="var(--text-subtle)" ta="center">
          Scanning a report&apos;s QR code opens this page. It confirms the report is genuine and never shows results.
        </Text>
      </Stack>
    </Box>
  );
}

function Row({ label, children }) {
  return (
    <div>
      <Text size="xs" c="var(--text-muted)" tt="uppercase" style={{ letterSpacing: '0.06em' }}>{label}</Text>
      <Text fw={500}>{children}</Text>
    </div>
  );
}

/** Public: what a scan of a report's QR code shows. Genuine reports get the details; anything else a clear "not found". */
export function ReportCheck({ code }) {
  const check = useReportCheck(code);

  if (!isReportCode(code) || check.isError) {
    return (
      <Frame>
        <GlowCard p="lg" role="status">
          <Group gap="md" align="flex-start" wrap="nowrap">
            <ThemeIcon size={44} radius="xl" variant="light" style={{ background: 'var(--warning-soft)', color: 'var(--warning)' }}>
              <IconShieldOff size={24} />
            </ThemeIcon>
            <div>
              <Text fw={600} fz="lg">We couldn&apos;t find this report</Text>
              <Text size="sm" c="var(--text-muted)" mt={4}>
                The code isn&apos;t one this clinic issued. If you were handed a printed report, don&apos;t rely on it — ask the clinic to confirm it.
              </Text>
            </div>
          </Group>
        </GlowCard>
      </Frame>
    );
  }

  if (check.isPending) {
    return (
      <Frame>
        <GlowCard p="xl">
          <Group justify="center" gap="sm"><Loader size="sm" color="dark" /><Text c="var(--text-muted)">Checking this report…</Text></Group>
        </GlowCard>
      </Frame>
    );
  }

  const r = check.data;
  return (
    <Frame>
      <GlowCard p="lg" role="status">
        <Group gap="md" align="flex-start" wrap="nowrap" mb="lg">
          <ThemeIcon size={44} radius="xl" variant="light" style={{ background: 'var(--success-soft)', color: 'var(--success)' }}>
            <IconCircleCheck size={26} />
          </ThemeIcon>
          <div>
            <Text fw={600} fz="lg">This is a genuine report</Text>
            <Text size="sm" c="var(--text-muted)">Issued by {r.clinicName}</Text>
          </div>
        </Group>
        <Stack gap="md">
          <Row label="Report number"><span className="mono">{r.reportNumber}</span></Row>
          <Row label="Patient">{r.patientInitials}</Row>
          <Row label="Tests">{r.tests.join(', ')}</Row>
          <Row label="Verified">{formatDateTime(r.verifiedAt)}</Row>
          <Box p="md" style={{ borderRadius: 'var(--radius-md)', border: '1.5px solid color-mix(in oklab, var(--success) 45%, transparent)', background: 'var(--success-soft)' }}>
            <Group gap={8} mb={4}>
              <IconShieldCheck size={18} color="var(--success)" />
              <Text size="xs" fw={700} tt="uppercase" c="var(--success)" style={{ letterSpacing: '0.08em' }}>Verified by</Text>
            </Group>
            <Text fw={600}>{r.verifier.name}</Text>
            {r.verifier.qualification && <Text size="sm" c="var(--text-muted)">{r.verifier.qualification}</Text>}
            {r.verifier.registrationNumber && <Text size="xs" c="var(--text-muted)" className="mono">Reg. no. {r.verifier.registrationNumber}</Text>}
          </Box>
          <Text size="xs" c="var(--text-muted)">
            Compare the report number, patient initials and tests above with the printed report. If they differ, the printout has been altered.
          </Text>
        </Stack>
      </GlowCard>
    </Frame>
  );
}
