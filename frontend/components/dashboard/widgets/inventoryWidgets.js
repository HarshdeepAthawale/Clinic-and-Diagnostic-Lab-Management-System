'use client';

import { Group, Stack, Text } from '@mantine/core';
import { IconPackage } from '@tabler/icons-react';
import Link from 'next/link';
import { categoryLabel } from '@/lib/inventory';
import { EmptyState } from '@/components/ui/EmptyState';
import { Panel } from '@/components/ui/Panel';
import { StockBadge, StockBar, StockCount } from '@/components/inventory/InventoryBits';
import { ListRow } from './ListRow';

const INVENTORY_PAGE = { ADMIN: '/admin/inventory', LAB_TECHNICIAN: '/lab/inventory' };

/** The emptiest items first; the backend only sends this card when something is below its level. */
export function LowStock({ widget, role }) {
  const { lowCount, outCount, items } = widget.data;
  const href = INVENTORY_PAGE[role];
  const subtitle = outCount > 0 ? `${lowCount} low · ${outCount} out of stock` : `${lowCount} below their level`;
  return (
    <Panel
      title={widget.title}
      subtitle={subtitle}
      right={href && <Link href={href} style={{ fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>Inventory</Link>}
    >
      {items.length === 0 ? (
        <EmptyState icon={IconPackage} title="Nothing is running low" compact />
      ) : (
        <Stack gap={2}>
          {items.map((item) => (
            <ListRow
              key={item.id}
              href={href}
              title={item.name}
              subtitle={categoryLabel(item.category)}
              right={
                <Group gap={10} wrap="nowrap">
                  <StockBar item={item} width={56} />
                  <StockCount item={item} />
                  <StockBadge item={item} size="xs" />
                </Group>
              }
            />
          ))}
        </Stack>
      )}
    </Panel>
  );
}
