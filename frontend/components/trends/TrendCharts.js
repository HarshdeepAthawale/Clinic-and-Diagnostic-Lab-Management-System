'use client';

import { LineChart } from '@mantine/charts';
import { Group, SimpleGrid, Skeleton, Stack, Text } from '@mantine/core';
import { IconArrowDownRight, IconArrowUpRight, IconChartLine, IconMinus } from '@tabler/icons-react';
import { friendlyMessage } from '@/lib/errors';
import { flagLabel, flagStatus } from '@/lib/results';
import { chartRows, groupByTest, latestFlag, referenceLines, trendChange, useTrends, yDomain } from '@/lib/trends';
import { shortDay } from '@/lib/insights';
import { ChartCard } from '@/components/insights/ChartCard';
import { EmptyState } from '@/components/ui/EmptyState';
import { StatusBadge } from '@/components/ui/StatusBadge';

/** A number without trailing zeros: 13.2, 140. */
const show = (x) => String(Number(x));

const DIRECTION = {
  up: { icon: IconArrowUpRight, word: 'Up' },
  down: { icon: IconArrowDownRight, word: 'Down' },
  steady: { icon: IconMinus, word: 'Steady' },
};

function Change({ series }) {
  const change = trendChange(series.points);
  if (!change) return null;
  const { icon: Icon, word } = DIRECTION[change.direction];
  return (
    <Group gap={4} wrap="nowrap">
      <Icon size={15} color="var(--text-muted)" />
      <Text size="xs" c="var(--text-muted)">
        {change.direction === 'steady' ? word : `${word} ${Math.abs(change.delta)} ${series.unit ?? ''}`.trim()} since the last visit
      </Text>
    </Group>
  );
}

/** One parameter across visits: the line, the normal range as reference lines, the latest value and how it moved. */
function TrendCard({ series }) {
  const rows = chartRows(series);
  const latest = series.points[series.points.length - 1];
  const flag = latestFlag(series);
  const unit = series.unit ? ` ${series.unit}` : '';
  return (
    <ChartCard
      title={series.name}
      subtitle={`${series.points.length} visits`}
      columns={[
        { key: 'date', label: 'Day', render: (r) => shortDay(r.date) },
        { key: 'sampleCode', label: 'Report' },
        { key: 'value', label: `Result${unit}`, align: 'right' },
        { key: 'flag', label: 'Flag', render: (r) => (r.flag ? flagLabel(r.flag) : '') },
      ]}
      rows={series.points.map((p) => ({ date: p.verifiedAt.slice(0, 10), sampleCode: p.sampleCode, value: show(p.value), flag: p.flag }))}
    >
      <Stack gap="xs">
        <Group justify="space-between" align="flex-end" wrap="nowrap">
          <Text fz={26} fw={700} lh={1.1} className="mono">
            {show(latest.value)}
            <Text span size="sm" c="var(--text-muted)" fw={500}>{unit}</Text>
          </Text>
          {flag && flag !== 'NORMAL' ? <StatusBadge status={flagStatus(flag)} label={flagLabel(flag)} /> : <StatusBadge status="normal" />}
        </Group>
        <Change series={series} />
        <LineChart
          h={170}
          data={rows}
          dataKey="date"
          series={[{ name: 'value', label: series.name, color: 'var(--accent)' }]}
          curveType="linear"
          withLegend={false}
          gridAxis="y"
          tickLine="none"
          dotProps={{ r: 3.5 }}
          activeDotProps={{ r: 5 }}
          referenceLines={referenceLines(series)}
          yAxisProps={{ domain: yDomain(series), width: 38 }}
          xAxisProps={{ tickFormatter: (d) => shortDay(d), minTickGap: 24 }}
          valueFormatter={(v) => `${show(v)}${unit}`}
          aria-label={`${series.name} across visits`}
        />
      </Stack>
    </ChartCard>
  );
}

/** Every parameter that has been measured on more than one visit, grouped by test. */
export function TrendGrid({ patientId }) {
  const trends = useTrends(patientId);

  if (trends.isPending) {
    return <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">{[0, 1].map((i) => <Skeleton key={i} height={300} radius="lg" />)}</SimpleGrid>;
  }
  if (trends.isError) {
    return <Text c="var(--critical)" size="sm">{friendlyMessage(trends.error)}</Text>;
  }
  if (trends.data.series.length === 0) {
    return (
      <EmptyState icon={IconChartLine} title="No trends yet" compact>
        A trend needs the same test on at least two visits. Numbers appear here once a repeat report is available.
      </EmptyState>
    );
  }
  return (
    <Stack gap="xl">
      {groupByTest(trends.data.series).map((group) => (
        <div key={group.testCode}>
          <Text fw={600} mb="sm">{group.testName}</Text>
          <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
            {group.series.map((s) => <TrendCard key={s.parameterId} series={s} />)}
          </SimpleGrid>
        </div>
      ))}
    </Stack>
  );
}
