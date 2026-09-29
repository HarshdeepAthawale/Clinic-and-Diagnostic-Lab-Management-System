'use client';

import { Box, Group, ScrollArea, Stack, Table, Text, Tooltip } from '@mantine/core';
import { formatDuration, heatGrid, heatLevel, shortDay, stageShares } from '@/lib/insights';
import { ChartCard } from './ChartCard';

const STAGE_TONES = ['var(--border-strong)', 'var(--ink)', 'var(--accent)'];

/** Where a test's time goes: to the lab, testing, verification — one bar, widths in proportion. */
function StageBar({ test }) {
  const stages = stageShares(test);
  if (stages.length === 0) return null;
  const summary = stages.map((s) => `${s.label} ${formatDuration(s.minutes)}`).join(' · ');
  return (
    <Tooltip label={summary} withArrow>
      <Group gap={2} wrap="nowrap" h={8} w={140} role="img" aria-label={summary} style={{ borderRadius: 999, overflow: 'hidden' }}>
        {stages.map((s, i) => (
          <Box key={s.label} h="100%" style={{ flex: Math.max(s.share, 0.04), background: STAGE_TONES[i % STAGE_TONES.length] }} />
        ))}
      </Group>
    </Tooltip>
  );
}

/** Turnaround by test: collection to report ready, with the spread (median, slowest 1 in 10) and retests. */
export function TatTable({ tat }) {
  return (
    <ChartCard
      title="Turnaround by test"
      subtitle="Collection to report ready · bar shows to the lab, testing, verification"
      columns={[
        { key: 'name', label: 'Test' },
        { key: 'samples', label: 'Samples', align: 'right' },
        { key: 'medianMinutes', label: 'Median', align: 'right', render: (r) => formatDuration(r.medianMinutes) },
        { key: 'p90Minutes', label: 'Slowest 1 in 10', align: 'right', render: (r) => formatDuration(r.p90Minutes) },
        { key: 'avgMinutes', label: 'Average', align: 'right', render: (r) => formatDuration(r.avgMinutes) },
        { key: 'retested', label: 'Retested', align: 'right' },
      ]}
      rows={tat}
    >
      {tat.length === 0 ? (
        <Text size="sm" c="var(--text-muted)" py="xl" ta="center">No reports were produced in this period.</Text>
      ) : (
        <Table.ScrollContainer minWidth={620}>
          <Table verticalSpacing="sm">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Test</Table.Th>
                <Table.Th ta="right">Samples</Table.Th>
                <Table.Th ta="right">Median</Table.Th>
                <Table.Th ta="right">Slowest 1 in 10</Table.Th>
                <Table.Th>Where the time goes</Table.Th>
                <Table.Th ta="right">Retested</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {tat.map((t) => (
                <Table.Tr key={t.testId}>
                  <Table.Td>
                    <Text size="sm" fw={600}>{t.name}</Text>
                    <Text size="xs" c="var(--text-subtle)" className="mono">{t.code}</Text>
                  </Table.Td>
                  <Table.Td ta="right"><Text size="sm" className="mono">{t.samples}</Text></Table.Td>
                  <Table.Td ta="right"><Text size="sm" fw={700} className="mono">{formatDuration(t.medianMinutes)}</Text></Table.Td>
                  <Table.Td ta="right"><Text size="sm" c="var(--text-muted)" className="mono">{formatDuration(t.p90Minutes)}</Text></Table.Td>
                  <Table.Td><StageBar test={t} /></Table.Td>
                  <Table.Td ta="right"><Text size="sm" className="mono" c={t.retested ? 'var(--warning)' : 'var(--text-subtle)'}>{t.retested}</Text></Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}
    </ChartCard>
  );
}

const CELL = 18;

/** Median turnaround per test per day: the darker the cell, the slower that day. Empty days stay blank. */
export function TatHeatmap({ tat, cells, days }) {
  const { rows, max } = heatGrid(tat, cells, days);
  const tableRows = rows.flatMap((r) =>
    r.cells.filter((c) => c.median !== null).map((c) => ({ test: r.test.name, date: c.date, median: c.median, samples: c.samples })),
  );
  return (
    <ChartCard
      title="Turnaround by day"
      subtitle="Median minutes per test per day · darker is slower"
      columns={[
        { key: 'test', label: 'Test' },
        { key: 'date', label: 'Day', render: (r) => shortDay(r.date) },
        { key: 'median', label: 'Median', align: 'right', render: (r) => formatDuration(r.median) },
        { key: 'samples', label: 'Samples', align: 'right' },
      ]}
      rows={tableRows}
    >
      {rows.length === 0 ? (
        <Text size="sm" c="var(--text-muted)" py="xl" ta="center">Nothing to compare yet — reports will fill this in.</Text>
      ) : (
        <Stack gap="sm">
          <ScrollArea type="auto" offsetScrollbars>
            <Box role="table" aria-label="Median turnaround per test per day" style={{ minWidth: 'max-content' }}>
              {rows.map((r) => (
                <Group key={r.test.testId} gap={10} wrap="nowrap" role="row" mb={3}>
                  <Text size="xs" fw={600} w={64} className="mono" truncate role="rowheader">{r.test.code}</Text>
                  <Group gap={2} wrap="nowrap">
                    {r.cells.map((c) => {
                      const level = heatLevel(c.median, max);
                      return (
                        <Tooltip
                          key={c.date}
                          disabled={level === null}
                          label={`${r.test.code} · ${shortDay(c.date)} · ${formatDuration(c.median)} (${c.samples} sample${c.samples === 1 ? '' : 's'})`}
                          withArrow
                        >
                          <Box
                            role="cell"
                            aria-label={level === null ? `${shortDay(c.date)}: none` : `${shortDay(c.date)}: ${formatDuration(c.median)}`}
                            w={CELL}
                            h={CELL}
                            style={{
                              borderRadius: 4,
                              background: level === null ? 'var(--surface-2)' : `color-mix(in srgb, var(--accent) ${Math.round(20 + level * 80)}%, white)`,
                            }}
                          />
                        </Tooltip>
                      );
                    })}
                  </Group>
                </Group>
              ))}
              <Group gap={10} wrap="nowrap" mt={6}>
                <Box w={64} />
                <Group justify="space-between" style={{ width: days.length * (CELL + 2) - 2 }}>
                  <Text size="xs" c="var(--text-subtle)">{shortDay(days[0])}</Text>
                  <Text size="xs" c="var(--text-subtle)">{shortDay(days[days.length - 1])}</Text>
                </Group>
              </Group>
            </Box>
          </ScrollArea>
          <Group gap={8}>
            <Text size="xs" c="var(--text-muted)">Faster</Text>
            {[0.1, 0.35, 0.6, 0.85, 1].map((l) => (
              <Box key={l} w={16} h={10} style={{ borderRadius: 3, background: `color-mix(in srgb, var(--accent) ${Math.round(20 + l * 80)}%, white)` }} />
            ))}
            <Text size="xs" c="var(--text-muted)">Slower (up to {formatDuration(max)})</Text>
          </Group>
        </Stack>
      )}
    </ChartCard>
  );
}
