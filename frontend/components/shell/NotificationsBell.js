'use client';

import { ActionIcon, Indicator, Popover, ScrollArea, Text, Tooltip, UnstyledButton } from '@mantine/core';
import { IconBell, IconChecks } from '@tabler/icons-react';
import Link from 'next/link';
import { parametersText, useCriticalAlerts, waitingFor } from '@/lib/critical';
import { useInventoryAlerts } from '@/lib/inventory';
import { useNotifications } from '@/lib/samples';
import { EmptyState } from '@/components/ui/EmptyState';
import { RedrawAlerts } from '@/components/lab/RedrawAlerts';
import { StockBadge, StockCount } from '@/components/inventory/InventoryBits';

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

const INVENTORY_PAGE = { ADMIN: '/admin/inventory', LAB_TECHNICIAN: '/lab/inventory' };
const CRITICAL_HOME = { DOCTOR: '/doctor', LAB_TECHNICIAN: '/lab' };

/** Critical results waiting for a doctor to acknowledge: the most urgent thing any bell can show. */
function CriticalInbox({ role, alerts }) {
  const items = alerts.data?.items ?? [];
  if (items.length === 0) return null;
  return (
    <div style={{ marginBottom: 14 }}>
      <Text fw={700} size="sm" c="var(--critical)" mb={6}>
        Critical results · {alerts.data.open}
      </Text>
      {items.slice(0, 4).map((a) => (
        <div key={a.sampleId} style={{ padding: '8px 0', borderTop: '1px solid var(--border)' }}>
          <Text size="sm" fw={600} truncate>{a.patientName}</Text>
          <Text size="xs" c="var(--critical)">{parametersText(a.parameters)}</Text>
          <Text size="xs" c="var(--text-muted)">waiting {waitingFor(a.verifiedAt)}</Text>
        </div>
      ))}
      <Link href={CRITICAL_HOME[role]} style={{ display: 'block', marginTop: 6, fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>
        {role === 'DOCTOR' ? 'Review and acknowledge' : 'See them on the bench'}
      </Link>
    </div>
  );
}

/** The lab's low-stock inbox: the emptiest items, with a link to record a restock. */
function StockInbox({ role, alerts }) {
  const items = alerts.data?.items ?? [];
  return (
    <>
      <Text fw={600} size="sm" mb={8}>
        Running low{alerts.data?.lowCount ? ` · ${alerts.data.lowCount}` : ''}
      </Text>
      {items.length === 0 ? (
        <EmptyState icon={IconChecks} title="Stock looks fine" compact>
          Items appear here when they fall below their low-stock level.
        </EmptyState>
      ) : (
        <ScrollArea.Autosize mah={360} type="auto" offsetScrollbars>
          {items.map((item) => (
            <div key={item.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, padding: '8px 0', borderTop: '1px solid var(--border)' }}>
              <Text size="sm" fw={600} truncate>{item.name}</Text>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', flex: 'none' }}>
                <StockCount item={item} />
                <StockBadge item={item} size="xs" />
              </div>
            </div>
          ))}
        </ScrollArea.Autosize>
      )}
      <Link href={INVENTORY_PAGE[role]} style={{ display: 'block', marginTop: 10, fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>
        Open inventory
      </Link>
    </>
  );
}

/**
 * Notification center. The front desk gets sample rejections and the lab and admin get low-stock items
 * (each with a count on the bell); other roles get their alerts here as later phases add them.
 * Inside the dock, `buttonClassName` / `iconClassName` / `labelClassName` give it the dock's look.
 */
export function NotificationsBell({ role, buttonClassName, iconClassName, labelClassName }) {
  const frontDesk = role === 'RECEPTIONIST';
  const stockKeeper = role === 'LAB_TECHNICIAN' || role === 'ADMIN';
  const inbox = useNotifications(frontDesk);
  const stock = useInventoryAlerts(stockKeeper);
  const criticalWatcher = role === 'DOCTOR' || role === 'LAB_TECHNICIAN';
  const critical = useCriticalAlerts(role, criticalWatcher);
  const open = frontDesk
    ? inbox.data?.open ?? 0
    : (stockKeeper ? stock.data?.lowCount ?? 0 : 0) + (criticalWatcher ? critical.data?.open ?? 0 : 0);

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
        ) : stockKeeper || criticalWatcher ? (
          <>
            {criticalWatcher && <CriticalInbox role={role} alerts={critical} />}
            {stockKeeper && <StockInbox role={role} alerts={stock} />}
            {!stockKeeper && !critical.data?.open && (
              <>
                <Text fw={600} size="sm" mb={4}>
                  Notifications
                </Text>
                <EmptyState icon={IconChecks} title="You’re all caught up" compact>
                  Critical results waiting for you will appear here.
                </EmptyState>
              </>
            )}
          </>
        ) : (
          <>
            <Text fw={600} size="sm" mb={4}>
              Notifications
            </Text>
            <EmptyState icon={IconChecks} title="You’re all caught up" compact>
              Alerts for your role will appear here.
            </EmptyState>
          </>
        )}
      </Popover.Dropdown>
    </Popover>
  );
}
