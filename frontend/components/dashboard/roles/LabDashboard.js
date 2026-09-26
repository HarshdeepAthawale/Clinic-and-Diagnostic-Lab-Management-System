'use client';

import { Box, Button, Grid, Group, Progress, SimpleGrid, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconArrowRight, IconChevronRight, IconQrcode, IconRefresh } from '@tabler/icons-react';
import { useState } from 'react';
import { LAB } from '@/lib/demo/data';
import { DemoBadge } from '@/components/ui/DemoBadge';
import { GlowCard } from '@/components/ui/GlowCard';
import { Panel } from '@/components/ui/Panel';
import { PhaseButton } from '@/components/ui/PhaseButton';
import { Reveal } from '@/components/ui/Reveal';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { TubeChip } from '@/components/ui/TubeChip';
import { DashboardHeader } from '../DashboardHeader';

const STAGES = [
  { key: 'toCollect', label: 'To collect' },
  { key: 'toReceive', label: 'To receive' },
  { key: 'inTesting', label: 'In testing' },
];

/** Lab bench home (Design.md §5.3): scan-first input, pipeline, pinned retest, queue with tube caps. */
export function LabDashboard() {
  const { pipeline, retest, queue, lowStock } = LAB;
  const [code, setCode] = useState('');

  const onScan = (event) => {
    event.preventDefault();
    if (!code.trim()) return;
    notifications.show({
      title: 'Sample lookup — coming in Phase 07',
      message: `Scanning ${code.trim().toUpperCase()} will jump straight to that sample's next step.`,
      color: 'dark',
      radius: 'lg',
    });
    setCode('');
  };

  return (
    <Stack gap={28}>
      <DashboardHeader role="LAB_TECHNICIAN" summary={`${pipeline.inTesting} samples in testing · 1 retest pinned`} />

      <Reveal y={10}>
        <GlowCard p="xl">
          <form onSubmit={onScan}>
            <Group gap="md" wrap="nowrap" align="flex-end">
              <TextInput
                label="Scan or type a sample code"
                placeholder="LAB-20260927-0044"
                size="xl"
                radius="lg"
                value={code}
                onChange={(e) => setCode(e.currentTarget.value)}
                leftSection={<IconQrcode size={24} stroke={1.5} />}
                styles={{ input: { fontFamily: 'var(--font-mono)', letterSpacing: '0.02em' }, label: { marginBottom: 8, fontWeight: 600 } }}
                style={{ flex: 1 }}
                autoFocus
              />
              <Button type="submit" size="xl" radius="lg" rightSection={<IconArrowRight size={18} />}>
                Open
              </Button>
            </Group>
          </form>
        </GlowCard>
      </Reveal>

      <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="lg">
        {STAGES.map((stage, i) => (
          <Reveal key={stage.key} delay={0.05 * i}>
            <GlowCard interactive>
              <Group justify="space-between" wrap="nowrap">
                <div>
                  <Text size="sm" c="var(--text-muted)" fw={500}>
                    {stage.label}
                  </Text>
                  <Text fz={34} fw={600} className="mono" lh={1.1}>
                    {pipeline[stage.key]}
                  </Text>
                </div>
                {i < STAGES.length - 1 ? (
                  <IconArrowRight size={22} color="var(--text-subtle)" />
                ) : (
                  <IconChevronRight size={22} color="var(--text-subtle)" />
                )}
              </Group>
            </GlowCard>
          </Reveal>
        ))}
      </SimpleGrid>

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, lg: 8 }}>
          <Reveal delay={0.1}>
            <Panel title="Bench queue" subtitle="Retests pinned first, then by due time" right={<DemoBadge phase={7} />}>
              <Box
                p="md"
                mb="md"
                style={{
                  borderRadius: 'var(--radius-md)',
                  background: 'var(--warning-soft)',
                  border: '1px solid color-mix(in oklab, var(--warning) 35%, transparent)',
                }}
              >
                <Group justify="space-between" wrap="nowrap" mb={6}>
                  <Group gap={8} wrap="nowrap">
                    <IconRefresh size={16} color="var(--warning)" />
                    <Text size="xs" fw={700} tt="uppercase" c="var(--warning)" style={{ letterSpacing: '0.06em' }}>
                      Returned for retest · attempt #{retest.attempt}
                    </Text>
                  </Group>
                  <TubeChip tube={retest.tube} />
                </Group>
                <Text fw={600}>
                  {retest.test} — {retest.patient}
                </Text>
                <Text size="sm" c="var(--text-muted)">
                  “{retest.reason}” · {retest.by} · <span className="mono">{retest.code}</span>
                </Text>
              </Box>

              <Stack gap={0}>
                {queue.map((item) => (
                  <Box key={item.code} py={12} style={{ borderTop: '1px solid var(--border)' }}>
                    <Group wrap="nowrap" gap="md">
                      <Text className="mono" size="xs" c="var(--text-muted)" w={92}>
                        {item.code.replace('LAB-20260927-', '…')}
                      </Text>
                      <Box style={{ flex: 1, minWidth: 0 }}>
                        <Text size="sm" fw={600} truncate>
                          {item.tests}
                        </Text>
                        <Text size="xs" c="var(--text-muted)" truncate>
                          {item.patient}
                        </Text>
                      </Box>
                      <TubeChip tube={item.tube} />
                      <Box w={96} visibleFrom="sm">
                        <StatusBadge status={item.status} />
                      </Box>
                      <Text className="mono" size="xs" c="var(--text-muted)" w={40} ta="right">
                        {item.due}
                      </Text>
                    </Group>
                    {item.mismatch && (
                      <Group gap={6} mt={8} ml={{ base: 0, sm: 108 }} wrap="nowrap">
                        <IconAlertTriangle size={14} color="var(--warning)" />
                        <Text size="xs" fw={600} c="var(--warning)">
                          Tube mismatch: {item.mismatch}
                        </Text>
                      </Group>
                    )}
                  </Box>
                ))}
              </Stack>
            </Panel>
          </Reveal>
        </Grid.Col>

        <Grid.Col span={{ base: 12, lg: 4 }}>
          <Reveal delay={0.15}>
            <Panel title="Low stock" subtitle="Below reorder threshold" right={<DemoBadge phase={9} />}>
              <Stack gap="lg">
                {lowStock.map((s) => (
                  <div key={s.item}>
                    <Group justify="space-between" mb={6} wrap="nowrap">
                      <Text size="sm" fw={600} truncate>
                        {s.item}
                      </Text>
                      <StatusBadge status="low-stock" />
                    </Group>
                    <Progress value={(s.left / s.threshold) * 100} color="yellow" size="sm" radius="xl" />
                    <Text size="xs" c="var(--text-muted)" mt={6} className="mono">
                      {s.left} / {s.threshold} {s.unit}
                    </Text>
                  </div>
                ))}
                <PhaseButton phase={9} what="Raises a restock request for everything below threshold." variant="default" fullWidth>
                  Request restock
                </PhaseButton>
              </Stack>
            </Panel>
          </Reveal>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
