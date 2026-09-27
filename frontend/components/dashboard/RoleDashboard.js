'use client';

import { Alert, Button, Grid, SimpleGrid, Skeleton, Stack } from '@mantine/core';
import { IconAlertCircle } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { friendlyMessage } from '@/lib/errors';
import { roleConfig } from '@/lib/roles';
import { Reveal } from '@/components/ui/Reveal';
import { DashboardHeader } from './DashboardHeader';
import { WIDGETS } from './widgets';

const SPAN = { full: { base: 12 }, wide: { base: 12, lg: 8 }, narrow: { base: 12, lg: 4 } };

export function useDashboard(role) {
  const config = roleConfig(role);
  return useQuery({
    queryKey: ['dashboard', role],
    queryFn: ({ signal }) => api(config.dashboardEndpoint, { signal }),
    // Dashboards now carry the live queue, so refresh often enough that it feels live.
    refetchInterval: 15_000,
  });
}

function DashboardSkeleton() {
  return (
    <Stack gap="lg">
      <SimpleGrid cols={{ base: 2, md: 4 }} spacing="lg">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} height={118} radius="xl" />
        ))}
      </SimpleGrid>
      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, lg: 8 }}>
          <Skeleton height={320} radius="xl" />
        </Grid.Col>
        <Grid.Col span={{ base: 12, lg: 4 }}>
          <Skeleton height={320} radius="xl" />
        </Grid.Col>
      </Grid>
    </Stack>
  );
}

/**
 * Any role's home: header + the widgets the backend returns for that role (real data only).
 * Refreshes every 15 seconds so the queue and counts stay current on screens left open at a desk.
 */
export function RoleDashboard({ role }) {
  const dashboard = useDashboard(role);

  return (
    <Stack gap={28}>
      <DashboardHeader role={role} />
      {dashboard.isPending ? (
        <DashboardSkeleton />
      ) : dashboard.isError ? (
        <Alert color="red" variant="light" radius="lg" icon={<IconAlertCircle size={18} />} title="Couldn't load your dashboard">
          {friendlyMessage(dashboard.error)}{' '}
          <Button size="xs" variant="white" color="red" onClick={() => dashboard.refetch()} ml="sm">
            Try again
          </Button>
        </Alert>
      ) : (
        <Grid gutter="lg">
          {dashboard.data.widgets.map((widget, i) => {
            const Widget = WIDGETS[widget.type];
            if (!Widget) return null;
            return (
              <Grid.Col key={`${widget.type}-${i}`} span={SPAN[widget.span] ?? SPAN.full}>
                <Reveal delay={0.05 * i}>
                  <Widget widget={widget} role={role} />
                </Reveal>
              </Grid.Col>
            );
          })}
        </Grid>
      )}
    </Stack>
  );
}
