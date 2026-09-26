'use client';

import { Box, Grid, Group, SimpleGrid, Stack, Text } from '@mantine/core';
import { IconCalendarEvent, IconCash, IconClockHour4, IconPhone, IconPlus, IconWalk } from '@tabler/icons-react';
import { motion } from 'motion/react';
import { RECEPTION } from '@/lib/demo/data';
import { DemoBadge } from '@/components/ui/DemoBadge';
import { KpiTile } from '@/components/ui/KpiTile';
import { Panel } from '@/components/ui/Panel';
import { PhaseButton } from '@/components/ui/PhaseButton';
import { Reveal } from '@/components/ui/Reveal';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { DashboardHeader } from '../DashboardHeader';

const WAIT_WARNING_MIN = 20;

const COLUMNS = [
  { key: 'waiting', title: 'Waiting' },
  { key: 'withDoctor', title: 'With doctor' },
  { key: 'done', title: 'Done' },
];

function TokenCard({ item, column, index }) {
  const late = column === 'waiting' && item.waited >= WAIT_WARNING_MIN;
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, delay: 0.04 * index, ease: [0.22, 1, 0.36, 1] }}
    >
      <Box
        className="lift"
        p="sm"
        style={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-md)',
          opacity: column === 'done' ? 0.6 : 1,
          boxShadow: late ? 'inset 3px 0 0 var(--warning)' : 'var(--shadow-sm)',
        }}
      >
        <Group justify="space-between" wrap="nowrap" mb={4}>
          <Text className="mono" fw={600} size="lg" lh={1}>
            {item.token}
          </Text>
          {column === 'waiting' && (
            <StatusBadge status={late ? 'waiting' : 'booked'} label={`${item.waited} min`} />
          )}
          {column === 'withDoctor' && <StatusBadge status="in-consult" label={item.room} />}
          {column === 'done' && <StatusBadge status="done" />}
        </Group>
        <Text size="sm" fw={600} truncate>
          {item.name}
        </Text>
        <Text size="xs" c="var(--text-muted)" truncate>
          {item.doctor} · {item.type}
        </Text>
      </Box>
    </motion.div>
  );
}

/** Reception home (Design.md §5.4): live token board, rejection inbox, arrivals. */
export function ReceptionDashboard() {
  const { kpis, board, rejections } = RECEPTION;

  return (
    <Stack gap={28}>
      <DashboardHeader
        role="RECEPTIONIST"
        summary={`${board.waiting.length} waiting · ${rejections.length} samples need a redraw call`}
        actions={
          <PhaseButton phase={3} what="Issues the next walk-in token for a doctor and adds it to the live queue. Shortcut: N." leftSection={<IconPlus size={16} />}>
            Issue walk-in token
          </PhaseButton>
        }
      />

      <SimpleGrid cols={{ base: 2, md: 4 }} spacing="lg">
        {[
          { label: 'Appointments today', value: kpis.appointments, icon: IconCalendarEvent },
          { label: 'Walk-ins', value: kpis.walkIns, icon: IconWalk },
          { label: 'Average wait', value: kpis.avgWaitMin, suffix: 'min', icon: IconClockHour4 },
          { label: 'Collected today', value: kpis.collections, format: (v) => `₹${Math.round(v).toLocaleString('en-IN')}`, icon: IconCash },
        ].map((kpi, i) => (
          <Reveal key={kpi.label} delay={0.05 * i}>
            <KpiTile {...kpi} />
          </Reveal>
        ))}
      </SimpleGrid>

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, lg: 8 }}>
          <Reveal delay={0.1}>
            <Panel
              title={
                <Group gap={8}>
                  <span className="live-dot" />
                  Live queue
                </Group>
              }
              subtitle={`Tokens waiting over ${WAIT_WARNING_MIN} minutes are marked amber`}
              right={<DemoBadge phase={3} />}
            >
              <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="md">
                {COLUMNS.map((column) => (
                  <Box key={column.key} p="xs" style={{ background: 'var(--surface-alt)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border)' }}>
                    <Group justify="space-between" px={4} mb="xs">
                      <Text size="xs" fw={700} tt="uppercase" c="var(--text-muted)" style={{ letterSpacing: '0.06em' }}>
                        {column.title}
                      </Text>
                      <Text size="xs" fw={600} className="mono" c="var(--text-subtle)">
                        {board[column.key].length}
                      </Text>
                    </Group>
                    <Stack gap={8}>
                      {board[column.key].map((item, i) => (
                        <TokenCard key={item.token} item={item} column={column.key} index={i} />
                      ))}
                    </Stack>
                  </Box>
                ))}
              </SimpleGrid>
            </Panel>
          </Reveal>
        </Grid.Col>

        <Grid.Col span={{ base: 12, lg: 4 }}>
          <Reveal delay={0.15}>
            <Panel title="Redraw needed" subtitle="Samples rejected by the lab" right={<DemoBadge phase={7} />}>
              <Stack gap="sm">
                {rejections.map((r) => (
                  <Box key={r.code} p="sm" style={{ borderRadius: 'var(--radius-md)', border: '1px solid var(--border)', background: 'var(--surface-alt)' }}>
                    <Group justify="space-between" wrap="nowrap" mb={6}>
                      <StatusBadge status="rejected" label={r.reason} />
                      <Text size="xs" c="var(--text-subtle)" className="mono">
                        {r.at}
                      </Text>
                    </Group>
                    <Text size="sm" fw={600}>
                      {r.patient}
                    </Text>
                    <Text size="xs" c="var(--text-muted)" mb="sm">
                      {r.test} · <span className="mono">{r.code}</span>
                    </Text>
                    <PhaseButton
                      phase={7}
                      what={`Logs the call to ${r.patient} (${r.phone}) and books a redraw slot.`}
                      size="xs"
                      variant="default"
                      leftSection={<IconPhone size={14} />}
                    >
                      Call & rebook
                    </PhaseButton>
                  </Box>
                ))}
              </Stack>
            </Panel>
          </Reveal>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
