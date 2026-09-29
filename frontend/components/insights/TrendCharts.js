'use client';

import { AreaChart, BarChart } from '@mantine/charts';
import { Text } from '@mantine/core';
import { formatMoney } from '@/lib/format';
import { shortDay, sum } from '@/lib/insights';
import { ChartCard } from './ChartCard';

const dayAxis = (iso) => shortDay(iso);

/** Money received each day (payments), as one area in the accent colour. */
export function RevenueChart({ series }) {
  const data = series.map((p) => ({ date: p.date, Revenue: Number(p.revenue) }));
  const total = sum(series, 'revenue');
  return (
    <ChartCard
      title="Revenue"
      subtitle={`${formatMoney(total)} collected in this period`}
      columns={[
        { key: 'date', label: 'Day', render: (r) => shortDay(r.date) },
        { key: 'revenue', label: 'Collected', align: 'right', render: (r) => formatMoney(r.revenue) },
      ]}
      rows={series}
    >
      {total === 0 ? (
        <Text size="sm" c="var(--text-muted)" py="xl" ta="center">No payments recorded in this period.</Text>
      ) : (
        <AreaChart
          h={240}
          data={data}
          dataKey="date"
          series={[{ name: 'Revenue', color: 'var(--accent)' }]}
          curveType="monotone"
          withDots={false}
          withLegend={false}
          gridAxis="y"
          tickLine="none"
          xAxisProps={{ tickFormatter: dayAxis, minTickGap: 24 }}
          valueFormatter={(v) => formatMoney(v)}
          areaChartProps={{ margin: { left: 4, right: 8, top: 8 } }}
          aria-label="Revenue per day"
        />
      )}
    </ChartCard>
  );
}

/** Patients seen (consultations finished) and new registrations per day. */
export function PatientsChart({ series }) {
  const data = series.map((p) => ({ date: p.date, 'Patients seen': p.patients, Registered: p.registrations }));
  const total = sum(series, 'patients') + sum(series, 'registrations');
  return (
    <ChartCard
      title="Patients"
      subtitle={`${sum(series, 'patients')} seen · ${sum(series, 'registrations')} registered`}
      columns={[
        { key: 'date', label: 'Day', render: (r) => shortDay(r.date) },
        { key: 'patients', label: 'Seen', align: 'right' },
        { key: 'registrations', label: 'Registered', align: 'right' },
      ]}
      rows={series}
    >
      {total === 0 ? (
        <Text size="sm" c="var(--text-muted)" py="xl" ta="center">No visits or registrations in this period.</Text>
      ) : (
        <BarChart
          h={240}
          data={data}
          dataKey="date"
          series={[
            { name: 'Patients seen', color: 'var(--accent)' },
            { name: 'Registered', color: 'var(--ink)' },
          ]}
          withLegend
          legendProps={{ verticalAlign: 'bottom', height: 28 }}
          gridAxis="y"
          tickLine="none"
          xAxisProps={{ tickFormatter: dayAxis, minTickGap: 24 }}
          aria-label="Patients per day"
        />
      )}
    </ChartCard>
  );
}
