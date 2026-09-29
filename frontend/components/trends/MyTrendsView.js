'use client';

import { Stack, Text } from '@mantine/core';
import { friendlyMessage } from '@/lib/errors';
import { useMyRecord } from '@/lib/patients';
import { PageTitle } from '@/components/ui/PageTitle';
import { TrendGrid } from './TrendCharts';

/** The patient's own numbers across visits, from the reports the lab has sent them. */
export function MyTrendsView() {
  const record = useMyRecord();
  return (
    <Stack gap="xl">
      <PageTitle
        title="Trends"
        subtitle="How your results have changed from one visit to the next. The grey lines mark the normal range."
      />
      {record.isError ? (
        <Text c="var(--critical)" size="sm">{friendlyMessage(record.error)}</Text>
      ) : (
        <TrendGrid patientId={record.data?.id} />
      )}
    </Stack>
  );
}
