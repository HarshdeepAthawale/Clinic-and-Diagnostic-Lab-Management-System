'use client';

import { Box, Grid, Group, Skeleton, Stack, Text, Title } from '@mantine/core';
import { IconCalendarEvent, IconDownload, IconInfoCircle, IconMapPin, IconReceipt, IconShieldCheck } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { useMe } from '@/lib/auth';
import { PATIENT } from '@/lib/demo/data';
import { DemoBadge } from '@/components/ui/DemoBadge';
import { GlowCard } from '@/components/ui/GlowCard';
import { Panel } from '@/components/ui/Panel';
import { PhaseButton } from '@/components/ui/PhaseButton';
import { RangeBar, rangeStatus } from '@/components/ui/RangeBar';
import { Reveal } from '@/components/ui/Reveal';
import { SampleJourney } from '@/components/ui/SampleJourney';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { greeting, longDate, shortName } from './greeting';

/** Patient home (Design.md §5.6): what matters right now, in plain language. */
export function PatientHome() {
  const { data: me } = useMe();
  // Confirms the patient area is reachable with this session (Phase 01 access check).
  useQuery({ queryKey: ['dashboard', 'PATIENT'], queryFn: ({ signal }) => api('/dashboard/patient', { signal }) });
  const { nextAppointment: appt, activeSample: sample, latestReport: report, bill } = PATIENT;

  return (
    <Stack gap={28}>
      <Reveal y={10}>
        <Text size="sm" c="var(--text-muted)" fw={500} mb={6}>
          {longDate()}
        </Text>
        {me ? (
          <Title order={1} fz={{ base: 30, sm: 40 }} fw={600} style={{ letterSpacing: '-0.03em' }}>
            {greeting()}, {shortName(me.name)}.
          </Title>
        ) : (
          <Skeleton height={44} width={300} radius="md" />
        )}
        <Text c="var(--text-muted)" mt={8} size="md">
          Your test is in the lab and your next visit is on Tuesday.
        </Text>
      </Reveal>

      <Reveal delay={0.05}>
        <GlowCard p="xl">
          <Group justify="space-between" align="flex-start" mb="xl" wrap="wrap" gap="sm">
            <div>
              <Group gap={8} mb={4}>
                <span className="live-dot" style={{ '--dot': 'var(--accent)' }} />
                <Text size="xs" fw={700} tt="uppercase" c="var(--accent)" style={{ letterSpacing: '0.06em' }}>
                  Tracking live
                </Text>
              </Group>
              <Text fw={600} size="lg">
                {sample.test}
              </Text>
              <Text size="sm" c="var(--text-muted)">
                Sample <span className="mono">{sample.code}</span> · {sample.eta}
              </Text>
            </div>
            <DemoBadge phase={7} />
          </Group>
          <SampleJourney current={sample.current} times={sample.times} currentNote="Now" label={`Progress of ${sample.test}`} />
        </GlowCard>
      </Reveal>

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: 5 }}>
          <Stack gap="lg" h="100%">
            <Reveal delay={0.1}>
              <Panel title="Next appointment" right={<DemoBadge phase={3} />}>
                <Group gap="md" wrap="nowrap" mb="md">
                  <Box
                    p={10}
                    ta="center"
                    style={{ borderRadius: 'var(--radius-md)', background: 'var(--accent-soft)', color: 'var(--accent)', minWidth: 64 }}
                  >
                    <Text size="xs" fw={700} tt="uppercase">Sep</Text>
                    <Text fz={26} fw={600} lh={1} className="mono">30</Text>
                  </Box>
                  <div>
                    <Text fw={600}>{appt.doctor}</Text>
                    <Text size="sm" c="var(--text-muted)">{appt.specialty}</Text>
                    <Group gap={6} mt={4} wrap="nowrap">
                      <IconCalendarEvent size={14} color="var(--text-muted)" />
                      <Text size="sm">{appt.when}</Text>
                    </Group>
                    <Group gap={6} wrap="nowrap">
                      <IconMapPin size={14} color="var(--text-muted)" />
                      <Text size="sm">{appt.where}</Text>
                    </Group>
                  </div>
                </Group>
                <Group gap={8} wrap="nowrap" align="flex-start" p="sm" style={{ borderRadius: 'var(--radius-sm)', background: 'var(--info-soft)' }}>
                  <IconInfoCircle size={16} color="var(--info)" style={{ flex: 'none', marginTop: 2 }} />
                  <Text size="sm">
                    <Text span fw={600}>Before your visit: </Text>
                    {appt.prep}
                  </Text>
                </Group>
              </Panel>
            </Reveal>

            <Reveal delay={0.15}>
              <Panel title="Bill due" right={<DemoBadge phase={6} />}>
                <Group justify="space-between" wrap="nowrap" mb="md">
                  <div>
                    <Text fz={28} fw={600} className="mono" lh={1.1}>
                      ₹{bill.amount.toLocaleString('en-IN')}
                    </Text>
                    <Text size="sm" c="var(--text-muted)">
                      <span className="mono">{bill.number}</span> · {bill.due}
                    </Text>
                  </div>
                  <StatusBadge status="due" />
                </Group>
                <PhaseButton phase={6} what="Shows the itemised invoice (consultation + tests) and how to pay at the counter." fullWidth leftSection={<IconReceipt size={16} />}>
                  View invoice
                </PhaseButton>
              </Panel>
            </Reveal>
          </Stack>
        </Grid.Col>

        <Grid.Col span={{ base: 12, md: 7 }}>
          <Reveal delay={0.1}>
            <Panel title={`Latest report · ${report.title}`} subtitle={report.date} right={<DemoBadge phase={8} />}>
              <Stack gap={0}>
                {report.results.map((r) => (
                  <Group key={r.test} wrap="nowrap" gap="md" py={14} style={{ borderTop: '1px solid var(--border)' }}>
                    <Box style={{ flex: 1, minWidth: 0 }}>
                      <Text fw={600} size="sm">{r.test}</Text>
                      <Text size="xs" c="var(--text-muted)" className="mono">
                        normal {r.low}–{r.high} {r.unit}
                      </Text>
                    </Box>
                    <RangeBar {...r} width={120} />
                    <Text className="mono" fw={600} w={70} ta="right">
                      {r.value}
                      <Text span size="xs" c="var(--text-subtle)"> {r.unit}</Text>
                    </Text>
                    <Box w={78} visibleFrom="xs">
                      <StatusBadge status={rangeStatus(r)} />
                    </Box>
                  </Group>
                ))}
              </Stack>
              <Group gap={8} mt="md" wrap="nowrap">
                <IconShieldCheck size={16} color="var(--success)" />
                <Text size="sm" c="var(--text-muted)">
                  Verified by {report.verifiedBy}
                </Text>
              </Group>
              <PhaseButton phase={8} what="Downloads the signed PDF report with the lab's letterhead and verification stamp." variant="default" mt="md" leftSection={<IconDownload size={16} />}>
                Download PDF
              </PhaseButton>
            </Panel>
          </Reveal>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
