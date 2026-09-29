'use client';

import { Alert, SegmentedControl, SimpleGrid, Skeleton, Stack, Text } from '@mantine/core';
import { IconAlertCircle, IconCash, IconClockHour4, IconFlask, IconReportMedical, IconUsers } from '@tabler/icons-react';
import { useState } from 'react';
import { friendlyMessage } from '@/lib/errors';
import { formatMoney } from '@/lib/format';
import { formatDuration, pctChange, periodDays, RANGES, shortDay, trend, useInsights } from '@/lib/insights';
import { KpiTile } from '@/components/ui/KpiTile';
import { PageTitle } from '@/components/ui/PageTitle';
import { Reveal } from '@/components/ui/Reveal';
import { StaffTable } from './StaffTable';
import { TatHeatmap, TatTable } from './TatSection';
import { TopTestsChart } from './TopTestsChart';
import { PatientsChart, RevenueChart } from './TrendCharts';

function Kpis({ data }) {
  const { kpis, series, days } = data;
  const vs = `vs previous ${days} days`;
  const tatChange = pctChange(kpis.medianTatMinutes, kpis.medianTatPreviousMinutes);
  return (
    <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }} spacing="lg">
      <KpiTile
        label="Patients seen"
        value={kpis.patientsInPeriod}
        delta={pctChange(kpis.patientsInPeriod, kpis.patientsPrevious) ?? undefined}
        deltaLabel={vs}
        caption={`${kpis.patientsToday} today`}
        trend={trend(series, 'patients')}
        icon={IconUsers}
      />
      <KpiTile
        label="Revenue"
        value={Number(kpis.revenueInPeriod)}
        format={formatMoney}
        delta={pctChange(kpis.revenueInPeriod, kpis.revenuePrevious) ?? undefined}
        deltaLabel={vs}
        caption={`${formatMoney(kpis.revenueToday)} today`}
        trend={trend(series, 'revenue')}
        icon={IconCash}
        tone="var(--accent)"
      />
      <KpiTile
        label="Reports issued"
        value={kpis.reportsInPeriod}
        delta={pctChange(kpis.reportsInPeriod, kpis.reportsPrevious) ?? undefined}
        deltaLabel={vs}
        caption={`${kpis.samplesInProgress} samples in progress`}
        trend={trend(series, 'reports')}
        icon={IconReportMedical}
      />
      <KpiTile
        label="Median turnaround"
        value={kpis.medianTatMinutes ?? 0}
        format={(v) => (kpis.medianTatMinutes === undefined ? '—' : formatDuration(v))}
        delta={tatChange ?? undefined}
        deltaLabel={vs}
        goodWhenUp={false}
        caption={kpis.medianTatMinutes === undefined ? 'No reports in this period' : 'Collection to report ready'}
        icon={IconClockHour4}
      />
    </SimpleGrid>
  );
}

/** The admin's picture of the clinic over a period: money, patients, tests and how fast the lab works. */
export function InsightsView() {
  const [days, setDays] = useState('30');
  const insights = useInsights(days);
  const data = insights.data;

  return (
    <Stack gap="xl">
      <PageTitle
        title="Insights"
        subtitle={data ? `${shortDay(data.from)} – ${shortDay(data.to)} · counted from the clinic's own records` : 'Counted from the clinic’s own records'}
        actions={<SegmentedControl size="xs" value={days} onChange={setDays} data={RANGES} aria-label="Period" />}
      />

      {insights.isPending ? (
        <Stack gap="lg">
          <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }} spacing="lg">{[0, 1, 2, 3].map((i) => <Skeleton key={i} height={120} radius="lg" />)}</SimpleGrid>
          <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">{[0, 1].map((i) => <Skeleton key={i} height={320} radius="lg" />)}</SimpleGrid>
        </Stack>
      ) : insights.isError ? (
        <Alert color="red" variant="light" icon={<IconAlertCircle size={16} />}>{friendlyMessage(insights.error)}</Alert>
      ) : (
        <Reveal y={8}>
          <Stack gap="lg" style={{ opacity: insights.isPlaceholderData ? 0.6 : 1, transition: 'opacity var(--duration-base) var(--ease-out)' }}>
            <Kpis data={data} />
            <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
              <RevenueChart series={data.series} />
              <PatientsChart series={data.series} />
            </SimpleGrid>
            <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
              <TopTestsChart tests={data.topTests} />
              <TatTable tat={data.tat} />
            </SimpleGrid>
            <TatHeatmap tat={data.tat} cells={data.heatmap} days={periodDays(data.series)} />
            <StaffTable staff={data.staff} />
            <Text size="xs" c="var(--text-subtle)">
              Revenue is money received, not billed. Patients seen are finished consultations. Turnaround is measured from each sample&apos;s own status log.
            </Text>
          </Stack>
        </Reveal>
      )}
    </Stack>
  );
}
