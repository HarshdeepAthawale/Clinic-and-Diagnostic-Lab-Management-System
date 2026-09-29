'use client';

import { Button, Group, SegmentedControl, Select, Skeleton, Stack, Switch, Table, Text, TextInput, UnstyledButton } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconAlertTriangle, IconPackage, IconPlus, IconSearch } from '@tabler/icons-react';
import { useState } from 'react';
import { friendlyMessage } from '@/lib/errors';
import { CATEGORIES, categoryLabel, useInventory, useInventoryAlerts } from '@/lib/inventory';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { StockBadge, StockBar, StockCount } from './InventoryBits';
import { ItemEditor } from './ItemEditor';
import { ItemPanel } from './ItemPanel';

/**
 * The lab's consumables: what is on the shelf, what is running low, and a place to record restocks and use.
 * Lab technicians and admins record changes; only admins add, edit or retire items.
 */
export function InventoryView({ canManage = false }) {
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState(null);
  const [show, setShow] = useState('all');
  const [showRetired, setShowRetired] = useState(false);
  const [openId, setOpenId] = useState(null);
  const [editing, setEditing] = useState(null); // null = closed, 'new', or an item
  const [debounced] = useDebouncedValue(query, 200);

  const items = useInventory({ q: debounced.trim(), category, lowOnly: show === 'low', includeInactive: showRetired });
  const alerts = useInventoryAlerts();
  const open = (items.data ?? []).find((i) => i.id === openId) ?? null;

  return (
    <Stack gap="xl">
      <PageTitle
        title="Inventory"
        subtitle="Tubes, reagents and consumables. Record what comes in and what is used, and the lab is warned before anything runs out."
        actions={canManage && <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')}>Add item</Button>}
      />

      <GlowCard p="lg">
        <Group justify="space-between" mb="md" wrap="wrap" gap="sm">
          <Group gap="sm" wrap="wrap">
            <TextInput
              leftSection={<IconSearch size={16} />}
              placeholder="Search items"
              value={query}
              onChange={(e) => setQuery(e.currentTarget.value)}
              w={{ base: '100%', sm: 260 }}
              aria-label="Search items"
            />
            <Select
              placeholder="All categories"
              data={CATEGORIES}
              value={category}
              onChange={setCategory}
              clearable
              w={{ base: '100%', sm: 190 }}
              aria-label="Category"
            />
          </Group>
          <Group gap="md">
            {canManage && <Switch size="xs" label="Show retired" checked={showRetired} onChange={(e) => setShowRetired(e.currentTarget.checked)} />}
            <SegmentedControl
              size="xs"
              value={show}
              onChange={setShow}
              data={[
                { label: 'All', value: 'all' },
                { label: `Running low${alerts.data?.lowCount ? ` · ${alerts.data.lowCount}` : ''}`, value: 'low' },
              ]}
            />
          </Group>
        </Group>

        {items.isPending ? (
          <Stack gap="xs">{[0, 1, 2, 3].map((i) => <Skeleton key={i} height={48} radius="md" />)}</Stack>
        ) : items.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(items.error)}</Text>
        ) : items.data.length === 0 ? (
          <EmptyState icon={show === 'low' ? IconAlertTriangle : IconPackage} title={show === 'low' ? 'Nothing is running low' : 'No items here'} compact>
            {show === 'low'
              ? 'Every item is above its low-stock level.'
              : query || category
                ? 'Nothing matches those filters.'
                : canManage
                  ? 'Add the first item to start tracking stock.'
                  : 'An admin can add the items the lab uses.'}
          </EmptyState>
        ) : (
          <Table.ScrollContainer minWidth={640}>
            <Table verticalSpacing="sm" highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Item</Table.Th>
                  <Table.Th>In stock</Table.Th>
                  <Table.Th>Level</Table.Th>
                  <Table.Th ta="right">Flag below</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {items.data.map((item) => (
                  <Table.Tr key={item.id} style={{ cursor: 'pointer', opacity: item.active ? 1 : 0.6 }} onClick={() => setOpenId(item.id)}>
                    <Table.Td>
                      <UnstyledButton onClick={() => setOpenId(item.id)} style={{ textAlign: 'left' }}>
                        <Text size="sm" fw={600}>{item.name}</Text>
                        <Text size="xs" c="var(--text-subtle)">{categoryLabel(item.category)}</Text>
                      </UnstyledButton>
                    </Table.Td>
                    <Table.Td><StockCount item={item} /></Table.Td>
                    <Table.Td><StockBar item={item} /></Table.Td>
                    <Table.Td ta="right"><Text size="sm" c="var(--text-muted)" className="mono">{item.lowStockThreshold || '—'}</Text></Table.Td>
                    <Table.Td ta="right"><StockBadge item={item} /></Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </GlowCard>

      <ItemPanel item={open} opened={Boolean(open)} onClose={() => setOpenId(null)} onEdit={canManage && open ? () => setEditing(open) : undefined} />
      <ItemEditor opened={editing !== null} item={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />
    </Stack>
  );
}
