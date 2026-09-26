'use client';

import { Badge, Box, Grid, Group, Kbd, ScrollArea, SimpleGrid, Stack, Table, Text } from '@mantine/core';
import { BarChart } from '@mantine/charts';
import { IconAlertOctagon, IconChecks, IconFocus2, IconListCheck, IconRefresh } from '@tabler/icons-react';
import { PATHOLOGIST } from '@/lib/demo/data';
import { DemoBadge } from '@/components/ui/DemoBadge';
import { GlowCard } from '@/components/ui/GlowCard';
import { KpiTile } from '@/components/ui/KpiTile';
import { Panel } from '@/components/ui/Panel';
import { PhaseButton } from '@/components/ui/PhaseButton';
import { RangeBar, rangeStatus } from '@/components/ui/RangeBar';
import { Reveal } from '@/components/ui/Reveal';
import { SafetyBanner } from '@/components/ui/SafetyBanner';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { DashboardHeader } from '../DashboardHeader';

const SEVERITY = { critical: 0, high: 1, low: 1, normal: 2 };
const HOURS = ['9', '10', '11', '12', '1', '2', '3'];

/** Pathologist home (Design.md §5.2): critical-first verification queue and focus-mode entry. */
export function PathologistDashboard() {
  const { kpis, queue, hourly } = PATHOLOGIST;
  const rows = [...queue]
    .map((r) => ({ ...r, status: rangeStatus(r) }))
    .sort((a, b) => SEVERITY[a.status] - SEVERITY[b.status]);
  const critical = rows.filter((r) => r.status === 'critical');

  return (
    <Stack gap={28}>
      <DashboardHeader
        role="PATHOLOGIST"
        summary={`${kpis.pending} results waiting for sign-off · oldest waiting ${queue[0].waited}`}
        actions={
          <PhaseButton phase={8} what="One result at a time with range bar, patient trend and V / R / J / K keyboard sign-off." leftSection={<IconFocus2 size={16} />}>
            Enter focus mode
          </PhaseButton>
        }
      />

      {critical.length > 0 && (
        <Reveal y={8}>
          <SafetyBanner title={`${critical.length} critical values waiting:`}>
            {critical.map((r) => `${r.test} ${r.value} ${r.unit} (${r.patient})`).join(' · ')}
          </SafetyBanner>
        </Reveal>
      )}

      <SimpleGrid cols={{ base: 2, md: 4 }} spacing="lg">
        {[
          { label: 'Awaiting verification', value: kpis.pending, icon: IconListCheck },
          { label: 'Critical', value: kpis.critical, icon: IconAlertOctagon, tone: 'var(--critical)' },
          { label: 'Retests in progress', value: kpis.retests, icon: IconRefresh },
          { label: 'Verified today', value: kpis.verifiedToday, icon: IconChecks, trend: hourly },
        ].map((kpi, i) => (
          <Reveal key={kpi.label} delay={0.05 * i}>
            <KpiTile {...kpi} />
          </Reveal>
        ))}
      </SimpleGrid>

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, lg: 8 }}>
          <Reveal delay={0.1}>
            <Panel title="Verification queue" subtitle="Critical first, then oldest" right={<DemoBadge phase={8} />}>
              <ScrollArea>
                <Table verticalSpacing={10} horizontalSpacing="sm" highlightOnHover miw={680}>
                  <Table.Thead>
                    <Table.Tr>
                      {['Sample', 'Patient · test', 'Result', '', 'Waited'].map((h) => (
                        <Table.Th key={h} fz="xs" c="var(--text-subtle)" fw={600} tt="uppercase" style={{ letterSpacing: '0.05em' }}>
                          {h}
                        </Table.Th>
                      ))}
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {rows.map((r) => (
                      <Table.Tr key={r.code}>
                        <Table.Td>
                          <Text className="mono" size="xs" c="var(--text-muted)">
                            {r.code.replace('LAB-20260927-', '…')}
                          </Text>
                          {r.attempt > 1 && (
                            <Badge size="xs" radius="sm" mt={4} variant="outline" color="yellow" styles={{ root: { textTransform: 'none' } }}>
                              Retest #{r.attempt}
                            </Badge>
                          )}
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm" fw={600}>
                            {r.test}
                          </Text>
                          <Text size="xs" c="var(--text-muted)">
                            {r.patient}
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          <Group gap="sm" wrap="nowrap">
                            <Text className="mono" size="sm" fw={600} w={54} ta="right">
                              {r.value}
                            </Text>
                            <Box>
                              <RangeBar {...r} width={100} />
                              <Text size="10px" c="var(--text-subtle)" className="mono" mt={4}>
                                ref {r.low}–{r.high} {r.unit}
                              </Text>
                            </Box>
                          </Group>
                        </Table.Td>
                        <Table.Td>
                          <StatusBadge status={r.status} />
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm" className="mono" c="var(--text-muted)">
                            {r.waited}
                          </Text>
                        </Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </ScrollArea>
            </Panel>
          </Reveal>
        </Grid.Col>

        <Grid.Col span={{ base: 12, lg: 4 }}>
          <Stack gap="lg">
            <Reveal delay={0.15}>
              <Panel title="Verified per hour" subtitle="Today" right={<DemoBadge phase={8} />}>
                <BarChart
                  h={160}
                  data={hourly.map((count, i) => ({ hour: HOURS[i], count }))}
                  dataKey="hour"
                  series={[{ name: 'count', label: 'Verified', color: 'var(--ink)' }]}
                  barProps={{ radius: 6 }}
                  gridAxis="none"
                  withYAxis={false}
                  tickLine="none"
                />
              </Panel>
            </Reveal>
            <Reveal delay={0.2}>
              <GlowCard>
                <Group gap="sm" mb="sm">
                  <IconFocus2 size={20} stroke={1.7} />
                  <Text fw={600}>Focus mode keys</Text>
                </Group>
                <Text size="sm" c="var(--text-muted)" mb="md">
                  Sign off a full queue without touching the mouse.
                </Text>
                <Stack gap={8}>
                  {[
                    ['V', 'Verify & sign'],
                    ['R', 'Return for retest (with reason)'],
                    ['J / K', 'Next / previous result'],
                    ['Esc', 'Back to queue'],
                  ].map(([key, label]) => (
                    <Group key={key} justify="space-between">
                      <Text size="sm">{label}</Text>
                      <Kbd size="sm">{key}</Kbd>
                    </Group>
                  ))}
                </Stack>
              </GlowCard>
            </Reveal>
          </Stack>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
