'use client';

import { Button, Stack, Text } from '@mantine/core';
import { IconChecks, IconPhoneCall } from '@tabler/icons-react';
import Link from 'next/link';
import { useHandleNotification } from '@/lib/samples';
import { formatRelative } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';

/**
 * Patients to call back after a sample was rejected. Each stays until the front desk marks it
 * handled. Shared by the bell, the dashboard card and the inbox page.
 */
export function RedrawAlerts({ items, compact = false }) {
  const handle = useHandleNotification();
  if (items.length === 0) {
    return (
      <EmptyState icon={IconChecks} title="You’re all caught up" compact>
        When the lab rejects a sample, the patient to call back appears here.
      </EmptyState>
    );
  }
  return (
    <Stack gap={compact ? 8 : 12}>
      {items.map((n) => (
        <div key={n.id} style={{ padding: compact ? '10px 12px' : '14px 16px', borderRadius: 'var(--radius-md)', border: '1px solid var(--border)', background: 'var(--surface-alt)' }}>
          <Text size="sm" fw={600}>{n.title}</Text>
          <Text size="xs" c="var(--text-muted)" mt={2} mb={8}>{n.message}</Text>
          <Stack gap={6}>
            <Text size="xs" c="var(--text-subtle)">{formatRelative(n.createdAt)}</Text>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <Button
                size="xs"
                leftSection={<IconPhoneCall size={14} />}
                loading={handle.isPending && handle.variables === n.id}
                onClick={() => handle.mutate(n.id)}
              >
                Patient called
              </Button>
              {n.patientId && (
                <Button size="xs" variant="default" component={Link} href={`/reception/patients/${n.patientId}`}>
                  Open record
                </Button>
              )}
            </div>
          </Stack>
        </div>
      ))}
    </Stack>
  );
}
