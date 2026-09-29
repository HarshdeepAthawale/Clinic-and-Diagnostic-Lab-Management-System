'use client';

import { BarChart } from '@mantine/charts';
import { Text } from '@mantine/core';
import { formatMoney } from '@/lib/format';
import { ChartCard } from './ChartCard';

/** The most-ordered tests in the period, as horizontal bars in the accent colour. */
export function TopTestsChart({ tests }) {
  const data = tests.map((t) => ({ test: t.code, Orders: t.orders }));
  return (
    <ChartCard
      title="Most-ordered tests"
      subtitle="Live order lines; cancelled ones are left out"
      columns={[
        { key: 'name', label: 'Test' },
        { key: 'orders', label: 'Orders', align: 'right' },
        { key: 'revenue', label: 'Billed', align: 'right', render: (r) => formatMoney(r.revenue) },
      ]}
      rows={tests}
    >
      {tests.length === 0 ? (
        <Text size="sm" c="var(--text-muted)" py="xl" ta="center">No tests were ordered in this period.</Text>
      ) : (
        <BarChart
          h={Math.max(160, tests.length * 34 + 30)}
          data={data}
          dataKey="test"
          orientation="vertical"
          series={[{ name: 'Orders', color: 'var(--accent)' }]}
          withLegend={false}
          gridAxis="x"
          tickLine="none"
          yAxisProps={{ width: 64 }}
          barProps={{ radius: 3 }}
          aria-label="Most-ordered tests"
        />
      )}
    </ChartCard>
  );
}
