'use client';

import { Avatar, Box, Grid, Group, SimpleGrid, Stack, Text, UnstyledButton } from '@mantine/core';
import { IconArrowRight, IconClipboardText, IconFlask, IconHourglass, IconUsers } from '@tabler/icons-react';
import { DOCTOR } from '@/lib/demo/data';
import { DemoBadge } from '@/components/ui/DemoBadge';
import { GlowCard } from '@/components/ui/GlowCard';
import { KpiTile } from '@/components/ui/KpiTile';
import { Panel } from '@/components/ui/Panel';
import { PhaseButton } from '@/components/ui/PhaseButton';
import { RangeBar, rangeStatus } from '@/components/ui/RangeBar';
import { Reveal } from '@/components/ui/Reveal';
import { SafetyBanner } from '@/components/ui/SafetyBanner';
import { Sparkline } from '@/components/ui/Sparkline';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { initials } from '@/components/shell/UserMenu';
import { DashboardHeader } from '../DashboardHeader';

function ScheduleRow({ slot }) {
  const current = slot.status === 'in-consult';
  return (
    <Group
      wrap="nowrap"
      gap="md"
      px="sm"
      py={10}
      style={{
        borderRadius: 'var(--radius-sm)',
        background: current ? 'var(--accent-soft)' : 'transparent',
        boxShadow: current ? 'inset 3px 0 0 var(--accent)' : 'none',
        opacity: slot.status === 'done' ? 0.62 : 1,
      }}
    >
      <Text className="mono" size="sm" fw={500} w={48} c={current ? 'var(--accent)' : 'var(--text-muted)'}>
        {slot.time}
      </Text>
      <Avatar size={30} radius="xl" styles={{ placeholder: { background: 'var(--surface-2)', color: 'var(--text)', fontSize: 11, fontWeight: 600 } }}>
        {initials(slot.name)}
      </Avatar>
      <Box style={{ flex: 1, minWidth: 0 }}>
        <Text size="sm" fw={600} truncate>
          {slot.name}{' '}
          <Text span size="xs" c="var(--text-subtle)" fw={500}>
            {slot.meta}
          </Text>
        </Text>
        <Text size="xs" c="var(--text-muted)" truncate>
          {slot.token && (
            <Text span className="mono" fw={600} c="var(--text)">
              {slot.token} ·{' '}
            </Text>
          )}
          {slot.reason}
        </Text>
      </Box>
      <StatusBadge status={slot.status} />
    </Group>
  );
}

/** Doctor "Today" (Design.md §5.5, §6): schedule, current patient with safety banner and labs, results ready. */
export function DoctorDashboard() {
  const { kpis, schedule, current, resultsReady } = DOCTOR;
  const next = schedule.find((s) => s.status === 'waiting');

  return (
    <Stack gap={28}>
      <DashboardHeader
        role="DOCTOR"
        summary={`${kpis.waiting} patients waiting · next up: ${next?.name} at ${next?.time}`}
        actions={
          <PhaseButton phase={4} what="Opens the consult workspace for your next patient, with notes, prescriptions and test orders.">
            Start next consult
          </PhaseButton>
        }
      />

      <SimpleGrid cols={{ base: 2, md: 4 }} spacing="lg">
        {[
          { label: 'Patients today', value: kpis.patientsToday, icon: IconUsers },
          { label: 'Seen', value: kpis.seen, icon: IconClipboardText },
          { label: 'Waiting now', value: kpis.waiting, icon: IconHourglass },
          { label: 'Results to review', value: kpis.resultsToReview, icon: IconFlask },
        ].map((kpi, i) => (
          <Reveal key={kpi.label} delay={0.05 * i}>
            <KpiTile {...kpi} />
          </Reveal>
        ))}
      </SimpleGrid>

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, lg: 7 }}>
          <Reveal delay={0.1}>
            <Panel title="Today's schedule" subtitle={`${schedule.length} appointments · 20-minute slots`} right={<DemoBadge phase={3} />}>
              <Stack gap={2}>
                {schedule.map((slot) => (
                  <ScheduleRow key={slot.time} slot={slot} />
                ))}
              </Stack>
            </Panel>
          </Reveal>
        </Grid.Col>

        <Grid.Col span={{ base: 12, lg: 5 }}>
          <Stack gap="lg">
            <Reveal delay={0.15}>
              <GlowCard>
                <Group justify="space-between" mb="md" wrap="nowrap">
                  <Group gap="sm" wrap="nowrap">
                    <span className="live-dot" style={{ '--dot': 'var(--accent)' }} />
                    <Text size="xs" fw={700} tt="uppercase" c="var(--accent)" style={{ letterSpacing: '0.06em' }}>
                      In consultation · Room 2
                    </Text>
                  </Group>
                  <DemoBadge phase={4} />
                </Group>
                <Group gap="md" wrap="nowrap" mb="md">
                  <Avatar size={48} radius="xl" styles={{ placeholder: { background: 'var(--ink)', color: 'var(--on-ink)', fontWeight: 600 } }}>
                    {initials(current.name)}
                  </Avatar>
                  <div>
                    <Text fw={600} size="lg" lh={1.2}>
                      {current.name}
                    </Text>
                    <Text size="sm" c="var(--text-muted)" className="mono">
                      {current.meta}
                    </Text>
                  </div>
                </Group>
                <SafetyBanner title="Allergy:">{current.allergies}</SafetyBanner>
                <Text size="sm" mt="md">
                  <Text span c="var(--text-muted)">Visit reason · </Text>
                  {current.reason}
                </Text>
                <SimpleGrid cols={4} spacing="xs" mt="md">
                  {current.vitals.map((v) => (
                    <Box key={v.label} p={10} style={{ borderRadius: 'var(--radius-sm)', background: 'var(--surface-alt)', border: '1px solid var(--border)' }}>
                      <Text size="xs" c="var(--text-muted)">{v.label}</Text>
                      <Text fw={600} className="mono" size="sm">
                        {v.value}
                        <Text span size="xs" c="var(--text-subtle)"> {v.unit}</Text>
                      </Text>
                    </Box>
                  ))}
                </SimpleGrid>
                <Text size="xs" fw={700} tt="uppercase" c="var(--text-subtle)" mt="lg" mb="xs" style={{ letterSpacing: '0.06em' }}>
                  Recent labs · last 4 results
                </Text>
                <Stack gap={10}>
                  {current.labs.map((lab) => (
                    <Group key={lab.test} wrap="nowrap" gap="sm">
                      <Text size="sm" fw={500} style={{ flex: 1 }} truncate>
                        {lab.test}
                      </Text>
                      <Sparkline values={lab.trend} width={56} height={22} color="var(--text-muted)" label={`${lab.test} trend`} />
                      <RangeBar {...lab} width={90} />
                      <Text className="mono" size="sm" fw={600} w={64} ta="right">
                        {lab.value}
                      </Text>
                      <Box w={74}>
                        <StatusBadge status={rangeStatus(lab)} />
                      </Box>
                    </Group>
                  ))}
                </Stack>
                <PhaseButton
                  phase={4}
                  what="Opens Asha's consult: notes, diagnosis, prescription builder and one-click test orders."
                  fullWidth
                  mt="lg"
                  rightSection={<IconArrowRight size={16} />}
                >
                  Open consult workspace
                </PhaseButton>
              </GlowCard>
            </Reveal>

            <Reveal delay={0.2}>
              <Panel title="Results ready" subtitle="Verified by the lab for your patients" right={<DemoBadge phase={8} />}>
                <Stack gap={4}>
                  {resultsReady.map((r) => (
                    <UnstyledButton key={r.patient + r.test} className="lift" px="sm" py={8} style={{ borderRadius: 'var(--radius-sm)', border: '1px solid transparent' }}>
                      <Group justify="space-between" wrap="nowrap">
                        <div style={{ minWidth: 0 }}>
                          <Text size="sm" fw={600} truncate>
                            {r.test}
                          </Text>
                          <Text size="xs" c="var(--text-muted)">
                            {r.patient} · {r.when}
                          </Text>
                        </div>
                        <StatusBadge status={r.flag} />
                      </Group>
                    </UnstyledButton>
                  ))}
                </Stack>
              </Panel>
            </Reveal>
          </Stack>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
