'use client';

import { Avatar, Box, Grid, Group, SimpleGrid, Stack, Text, Tooltip } from '@mantine/core';
import { AreaChart, BarChart } from '@mantine/charts';
import { IconCurrencyRupee, IconHourglassHigh, IconTestPipe, IconUsers } from '@tabler/icons-react';
import { ADMIN } from '@/lib/demo/data';
import { DemoBadge } from '@/components/ui/DemoBadge';
import { KpiTile } from '@/components/ui/KpiTile';
import { Panel } from '@/components/ui/Panel';
import { Reveal } from '@/components/ui/Reveal';
import { RoleBadge } from '@/components/ui/RoleBadge';
import { initials } from '@/components/shell/UserMenu';
import { DashboardHeader } from '../DashboardHeader';

const inr = (v) => `₹${Math.round(v).toLocaleString('en-IN')}`;

/** Turnaround-time heatmap. Each row is scaled to its own range, since a culture takes days and a CBC hours. */
function TatHeatmap({ days, rows }) {
  return (
    <Box style={{ overflowX: 'auto' }}>
      <Box style={{ display: 'grid', gridTemplateColumns: `minmax(96px, 150px) repeat(${days.length}, minmax(34px, 1fr))`, gap: 4, minWidth: 380 }}>
        <span />
        {days.map((d) => (
          <Text key={d} size="xs" c="var(--text-subtle)" ta="center" fw={600}>
            {d}
          </Text>
        ))}
        {rows.map((row) => {
          const min = Math.min(...row.hours);
          const max = Math.max(...row.hours);
          return [
            <Text key={row.test} size="sm" fw={500} truncate style={{ alignSelf: 'center' }}>
              {row.test}
            </Text>,
            ...row.hours.map((h, i) => {
              const t = (h - min) / (max - min || 1);
              const pct = Math.round(6 + t * 58);
              return (
                <Tooltip key={`${row.test}-${days[i]}`} label={`${row.test} · ${days[i]}: ${h} h average`}>
                  <Box
                    h={36}
                    style={{
                      display: 'grid',
                      placeItems: 'center',
                      borderRadius: 8,
                      background: `color-mix(in oklab, var(--accent) ${pct}%, var(--surface))`,
                      color: pct > 42 ? '#fff' : 'var(--text)',
                      transition: 'transform 150ms var(--ease-out)',
                    }}
                    className="mono"
                    fz={12}
                    fw={500}
                  >
                    {h >= 24 ? `${Math.round(h)}` : h.toFixed(1)}
                  </Box>
                </Tooltip>
              );
            }),
          ];
        })}
      </Box>
      <Group gap={8} mt="md" justify="flex-end">
        <Text size="xs" c="var(--text-subtle)">Faster</Text>
        <Box w={120} h={8} style={{ borderRadius: 4, background: 'linear-gradient(90deg, color-mix(in oklab, var(--accent) 6%, var(--surface)), var(--accent))' }} />
        <Text size="xs" c="var(--text-subtle)">Slower (per test)</Text>
      </Group>
    </Box>
  );
}

/** Admin insights (Design.md §5.7). */
export function AdminDashboard() {
  const { kpis, revenue, topTests, tatDays, tat, staff, accessLog } = ADMIN;

  return (
    <Stack gap={28}>
      <DashboardHeader role="ADMIN" summary="Clinic and lab performance · last 7 days unless noted" />

      <SimpleGrid cols={{ base: 1, xs: 2, lg: 4 }} spacing="lg">
        {[
          { label: 'Patients today', icon: IconUsers, ...kpis.patients },
          { label: 'Revenue today', icon: IconCurrencyRupee, format: inr, ...kpis.revenue },
          { label: 'Samples in flight', icon: IconTestPipe, ...kpis.inFlight, goodWhenUp: false },
          { label: 'Avg turnaround', icon: IconHourglassHigh, suffix: 'h', format: (v) => v.toFixed(1), goodWhenUp: false, ...kpis.tat },
        ].map((kpi, i) => (
          <Reveal key={kpi.label} delay={0.05 * i}>
            <KpiTile {...kpi} />
          </Reveal>
        ))}
      </SimpleGrid>

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, lg: 8 }}>
          <Reveal delay={0.1}>
            <Panel title="Revenue" subtitle="Last 14 days · ₹ thousands" right={<DemoBadge phase={9} />}>
              <AreaChart
                h={260}
                data={revenue}
                dataKey="day"
                type="stacked"
                series={[
                  { name: 'lab', label: 'Lab tests', color: 'var(--accent)' },
                  { name: 'consultations', label: 'Consultations', color: 'var(--ink)' },
                ]}
                curveType="monotone"
                withLegend
                legendProps={{ verticalAlign: 'top', height: 36 }}
                gridAxis="none"
                withDots={false}
                fillOpacity={0.16}
                tickLine="none"
                valueFormatter={(v) => `₹${v}k`}
              />
            </Panel>
          </Reveal>
        </Grid.Col>
        <Grid.Col span={{ base: 12, lg: 4 }}>
          <Reveal delay={0.15}>
            <Panel title="Most-ordered tests" subtitle="This month" right={<DemoBadge phase={9} />}>
              <BarChart
                h={260}
                data={topTests}
                dataKey="test"
                orientation="vertical"
                series={[{ name: 'orders', label: 'Orders', color: 'var(--ink)' }]}
                barProps={{ radius: 6 }}
                gridAxis="none"
                withXAxis={false}
                yAxisProps={{ width: 110 }}
                tickLine="none"
              />
            </Panel>
          </Reveal>
        </Grid.Col>

        <Grid.Col span={12}>
          <Reveal delay={0.1}>
            <Panel title="Turnaround time by test" subtitle="Average hours from collection to report, by weekday" right={<DemoBadge phase={9} />}>
              <TatHeatmap days={tatDays} rows={tat} />
            </Panel>
          </Reveal>
        </Grid.Col>

        <Grid.Col span={{ base: 12, lg: 7 }}>
          <Reveal delay={0.1}>
            <Panel title="Staff today" subtitle="Throughput by person" right={<DemoBadge phase={9} />}>
              <Stack gap={0}>
                {staff.map((s) => (
                  <Group key={s.name} wrap="nowrap" gap="md" py={12} style={{ borderTop: '1px solid var(--border)' }}>
                    <Avatar size={34} radius="xl" styles={{ placeholder: { background: 'var(--surface-2)', color: 'var(--text)', fontSize: 12, fontWeight: 600 } }}>
                      {initials(s.name)}
                    </Avatar>
                    <Box style={{ flex: 1, minWidth: 0 }}>
                      <Text size="sm" fw={600} truncate>
                        {s.name}
                      </Text>
                      <RoleBadge role={s.role} size="xs" />
                    </Box>
                    <Box ta="right">
                      <Text size="sm" fw={600} className="mono">
                        {s.metric}
                      </Text>
                      <Text size="xs" c="var(--text-muted)">
                        {s.detail}
                      </Text>
                    </Box>
                  </Group>
                ))}
              </Stack>
            </Panel>
          </Reveal>
        </Grid.Col>
        <Grid.Col span={{ base: 12, lg: 5 }}>
          <Reveal delay={0.15}>
            <Panel title="Record access log" subtitle="Who opened which patient record" right={<DemoBadge phase={2} />}>
              <Stack gap={0}>
                {accessLog.map((a) => (
                  <Group key={a.who + a.when} wrap="nowrap" justify="space-between" py={12} style={{ borderTop: '1px solid var(--border)' }}>
                    <Box style={{ minWidth: 0 }}>
                      <Text size="sm" truncate>
                        <Text span fw={600}>{a.who}</Text> opened <Text span fw={600}>{a.patient}</Text>
                      </Text>
                      <Text size="xs" c="var(--text-muted)">
                        {a.what}
                      </Text>
                    </Box>
                    <Text size="xs" className="mono" c="var(--text-subtle)">
                      {a.when}
                    </Text>
                  </Group>
                ))}
              </Stack>
            </Panel>
          </Reveal>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
