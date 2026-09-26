'use client';

import { Group, Skeleton, Text, Title, Tooltip } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { useMe } from '@/lib/auth';
import { roleConfig } from '@/lib/roles';
import { RoleBadge } from '@/components/ui/RoleBadge';
import { Reveal } from '@/components/ui/Reveal';
import { greeting, longDate, shortName } from './greeting';

/**
 * Page title row for every role home: date, greeting, one-line summary, role badge and a live
 * "session verified" indicator backed by the role's dashboard endpoint (Phase 01 access check).
 */
export function DashboardHeader({ role, summary, actions }) {
  const { data: me } = useMe();
  const config = roleConfig(role);
  const check = useQuery({
    queryKey: ['dashboard', role],
    queryFn: ({ signal }) => api(config.dashboardEndpoint, { signal }),
  });

  const status = check.isPending
    ? { dot: 'var(--text-subtle)', text: 'Checking access…' }
    : check.isError
      ? { dot: 'var(--critical)', text: 'Access check failed' }
      : { dot: 'var(--success)', text: 'Session verified' };

  return (
    <Reveal y={10}>
      <Group justify="space-between" align="flex-end" wrap="wrap" gap="md">
        <div>
          <Group gap={10} mb={6}>
            <Text size="sm" c="var(--text-muted)" fw={500}>
              {longDate()}
            </Text>
            <Tooltip label={`Your ${config.label.toLowerCase()} access is checked by the server on every request`}>
              <Group gap={6} wrap="nowrap" style={{ cursor: 'default' }}>
                <span className="live-dot" style={{ '--dot': status.dot }} />
                <Text size="xs" fw={600} c="var(--text-muted)">
                  {status.text}
                </Text>
              </Group>
            </Tooltip>
          </Group>
          {me ? (
            <Title order={1} fz={{ base: 26, sm: 30 }} fw={600} style={{ letterSpacing: '-0.02em' }}>
              {greeting()}, {shortName(me.name)}
            </Title>
          ) : (
            <Skeleton height={34} width={280} radius="md" />
          )}
          {summary && (
            <Text c="var(--text-muted)" mt={6} size="md">
              {summary}
            </Text>
          )}
        </div>
        <Group gap="sm">
          {actions}
          <RoleBadge role={role} size="lg" />
        </Group>
      </Group>
    </Reveal>
  );
}
