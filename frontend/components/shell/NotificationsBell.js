'use client';

import { ActionIcon, Popover, Text, Tooltip } from '@mantine/core';
import { IconBell, IconChecks } from '@tabler/icons-react';
import { EmptyState } from '@/components/ui/EmptyState';

/** Notification center. Rejections, critical values and queue events feed in from later phases. */
export function NotificationsBell() {
  return (
    <Popover position="bottom-end" width={320} shadow="md" radius="lg" withinPortal>
      <Popover.Target>
        <Tooltip label="Notifications">
          <ActionIcon variant="subtle" color="gray" size="lg" radius="md" aria-label="Notifications">
            <IconBell size={19} stroke={1.6} />
          </ActionIcon>
        </Tooltip>
      </Popover.Target>
      <Popover.Dropdown>
        <Text fw={600} size="sm" mb={4}>
          Notifications
        </Text>
        <EmptyState icon={IconChecks} title="You're all caught up" compact>
          Sample rejections, critical values and queue updates will appear here.
        </EmptyState>
      </Popover.Dropdown>
    </Popover>
  );
}
