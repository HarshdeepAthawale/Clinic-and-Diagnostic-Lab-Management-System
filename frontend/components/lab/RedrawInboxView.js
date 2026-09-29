'use client';

import { Skeleton, Stack, Text } from '@mantine/core';
import { useNotifications } from '@/lib/samples';
import { friendlyMessage } from '@/lib/errors';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { RedrawAlerts } from './RedrawAlerts';

/** The front desk's sample-rejected inbox (Design.md §6): patients to call back for a new sample. */
export function RedrawInboxView() {
  const inbox = useNotifications();
  return (
    <Stack gap="xl">
      <PageTitle
        title="Samples to redraw"
        subtitle="When the lab rejects a sample, the patient is listed here. Call them in, then mark it done — the new sample is already waiting at the lab."
      />
      <GlowCard p="lg">
        {inbox.isPending ? (
          <Stack gap="xs">{[0, 1].map((i) => <Skeleton key={i} height={96} radius="md" />)}</Stack>
        ) : inbox.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(inbox.error)}</Text>
        ) : (
          <RedrawAlerts items={inbox.data.items} />
        )}
      </GlowCard>
    </Stack>
  );
}
