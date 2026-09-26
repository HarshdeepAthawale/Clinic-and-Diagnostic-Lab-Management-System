'use client';

import { Alert, Button, Card, Group, SimpleGrid, Skeleton, Stack, Text, Title } from '@mantine/core';
import { IconAlertCircle, IconCalendarCheck, IconShieldCheck } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { useMe } from '@/lib/auth';
import { friendlyMessage } from '@/lib/errors';
import { roleConfig } from '@/lib/roles';
import { EmptyState } from '@/components/ui/EmptyState';
import { RoleBadge } from '@/components/ui/RoleBadge';
import { FeatureCard } from './FeatureCard';
import { greeting, longDate, shortName } from './greeting';

/** Phase 01 home for every staff role: greeting, verified session, and what's coming to this workspace. */
export function StaffDashboard({ role }) {
  const config = roleConfig(role);
  const { data: me } = useMe();
  const dashboard = useQuery({
    queryKey: ['dashboard', role],
    queryFn: ({ signal }) => api(config.dashboardEndpoint, { signal }),
  });

  return (
    <Stack gap={28}>
      <Group justify="space-between" align="flex-end" wrap="wrap" gap="md">
        <div>
          <Text size="sm" c="var(--text-muted)" fw={500} mb={4}>
            {longDate()}
          </Text>
          {me ? (
            <Title order={1} fz={{ base: 26, sm: 30 }} style={{ letterSpacing: '-0.02em' }}>
              {greeting()}, {shortName(me.name)}
            </Title>
          ) : (
            <Skeleton height={34} width={280} radius="md" />
          )}
        </div>
        <RoleBadge role={role} size="lg" />
      </Group>

      {dashboard.isPending ? (
        <Skeleton height={52} radius="lg" />
      ) : dashboard.isError ? (
        <Alert
          color="red"
          variant="light"
          radius="lg"
          icon={<IconAlertCircle size={18} />}
          title="Couldn't load your workspace"
        >
          <Group justify="space-between" wrap="nowrap">
            <span>{friendlyMessage(dashboard.error)}</span>
            <Button size="xs" variant="white" color="red" onClick={() => dashboard.refetch()}>
              Try again
            </Button>
          </Group>
        </Alert>
      ) : (
        <Group
          gap="sm"
          px="md"
          py={12}
          wrap="nowrap"
          style={{ border: '1px solid var(--border)', borderRadius: 'var(--radius-md)', background: 'var(--surface)' }}
        >
          <IconShieldCheck size={20} color="var(--success)" stroke={1.7} style={{ flex: 'none' }} />
          <Text size="sm">
            <Text span fw={600}>
              Signed in securely.
            </Text>{' '}
            <Text span c="var(--text-muted)">
              Your {config.label.toLowerCase()} access was verified by the server.
            </Text>
          </Text>
        </Group>
      )}

      <Card style={{ background: 'var(--surface)' }}>
        <EmptyState icon={IconCalendarCheck} title="Nothing needs your attention yet">
          {dashboard.data?.message ?? 'Your queues and schedules will appear here as each part of the system goes live.'}
        </EmptyState>
      </Card>

      <div>
        <Group justify="space-between" mb="md">
          <Title order={2} fz={18}>
            Coming to your workspace
          </Title>
          <Text size="sm" c="var(--text-muted)" visibleFrom="sm">
            Press <kbd style={{ fontFamily: 'var(--font-mono)' }}>Ctrl K</kbd> to search anywhere
          </Text>
        </Group>
        <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }} spacing="lg">
          {config.upcoming.map((feature, index) => (
            <FeatureCard key={feature.title} feature={feature} index={index} />
          ))}
        </SimpleGrid>
      </div>
    </Stack>
  );
}
