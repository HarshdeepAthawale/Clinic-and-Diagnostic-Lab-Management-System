'use client';

import { ActionIcon, Indicator, Popover, ScrollArea, Text, Tooltip, UnstyledButton } from '@mantine/core';
import { IconBell, IconChecks } from '@tabler/icons-react';
import Link from 'next/link';
import { useNotifications } from '@/lib/samples';
import { EmptyState } from '@/components/ui/EmptyState';
import { RedrawAlerts } from '@/components/lab/RedrawAlerts';

/** The front desk's live inbox: patients to call back after a rejected sample. */
function FrontDeskInbox() {
  const inbox = useNotifications();
  return (
    <>
      <Text fw={600} size="sm" mb={8}>
        Notifications{inbox.data?.open ? ` · ${inbox.data.open}` : ''}
      </Text>
      <ScrollArea.Autosize mah={420} type="auto" offsetScrollbars>
        <RedrawAlerts items={inbox.data?.items ?? []} compact />
      </ScrollArea.Autosize>
      {inbox.data?.open > 0 && (
        <Link href="/reception/samples" style={{ display: 'block', marginTop: 10, fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>
          Open the inbox
        </Link>
      )}
    </>
  );
}

/**
 * Notification center. The front desk gets sample rejections (with a count on the bell); other roles
 * get their alerts here as later phases add them.
 * Inside the dock, `buttonClassName` / `iconClassName` / `labelClassName` give it the dock's look.
 */
export function NotificationsBell({ role, buttonClassName, iconClassName, labelClassName }) {
  const frontDesk = role === 'RECEPTIONIST';
  const inbox = useNotifications(frontDesk);
  const open = frontDesk ? inbox.data?.open ?? 0 : 0;

  const icon = (
    <Indicator label={open > 9 ? '9+' : open} disabled={open === 0} size={16} offset={4} color="red" withBorder processing={open > 0}>
      <IconBell size={buttonClassName ? 20 : 19} stroke={1.7} className={iconClassName} />
    </Indicator>
  );

  return (
    <Popover position="bottom-end" width={340} shadow="md" radius="lg" withinPortal offset={16}>
      <Popover.Target>
        {buttonClassName ? (
          <UnstyledButton className={buttonClassName} aria-label={open ? `Notifications, ${open} open` : 'Notifications'}>
            {icon}
            {labelClassName && <span className={labelClassName}>Notifications</span>}
          </UnstyledButton>
        ) : (
          <Tooltip label="Notifications">
            <ActionIcon variant="subtle" color="gray" size="lg" radius="md" aria-label={open ? `Notifications, ${open} open` : 'Notifications'}>
              {icon}
            </ActionIcon>
          </Tooltip>
        )}
      </Popover.Target>
      <Popover.Dropdown>
        {frontDesk ? (
          <FrontDeskInbox />
        ) : (
          <>
            <Text fw={600} size="sm" mb={4}>
              Notifications
            </Text>
            <EmptyState icon={IconChecks} title="You’re all caught up" compact>
              Alerts for your role, like critical values, will appear here.
            </EmptyState>
          </>
        )}
      </Popover.Dropdown>
    </Popover>
  );
}
