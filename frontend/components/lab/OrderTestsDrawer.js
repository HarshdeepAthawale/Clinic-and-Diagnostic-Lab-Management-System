'use client';

import {
  Alert,
  Box,
  Button,
  Chip,
  Drawer,
  Group,
  Kbd,
  ScrollArea,
  SegmentedControl,
  Skeleton,
  Stack,
  Text,
  Textarea,
  TextInput,
  UnstyledButton,
} from '@mantine/core';
import { useDebouncedValue, useHotkeys } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconCheck, IconClockHour4, IconFlask, IconSearch, IconSalad } from '@tabler/icons-react';
import { useMemo, useState } from 'react';
import { formatTurnaround, groupByCategory, orderTotal, prepItems, slowestTurnaround, tubeSummary, useLabTests, useOrderTests } from '@/lib/lab';
import { friendlyMessage } from '@/lib/errors';
import { formatMoney } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { TubeChip } from '@/components/ui/TubeChip';
import { availablePanels, togglePanel } from './panels';
import classes from './OrderTestsDrawer.module.css';

function CheckMark({ on, locked }) {
  return (
    <Box
      className={classes.check}
      data-on={on || undefined}
      data-locked={locked || undefined}
      aria-hidden
    >
      {(on || locked) && <IconCheck size={12} stroke={3} />}
    </Box>
  );
}

function TestRow({ test, on, locked, onToggle }) {
  return (
    <UnstyledButton
      className={classes.row}
      data-on={on || undefined}
      disabled={locked}
      onClick={onToggle}
      aria-pressed={on}
    >
      <CheckMark on={on} locked={locked} />
      <Box style={{ flex: 1, minWidth: 0 }}>
        <Group gap={8} wrap="nowrap">
          <Text size="sm" fw={600} truncate>{test.name}</Text>
          {test.prepInstructions && (
            <IconSalad size={14} color="var(--warning)" aria-label="Needs preparation" style={{ flex: 'none' }} />
          )}
        </Group>
        <Group gap={8} mt={3} wrap="nowrap">
          <Text size="xs" c="var(--text-subtle)" className="mono">{test.code}</Text>
          <Text size="xs" c="var(--text-subtle)">·</Text>
          <Group gap={3} wrap="nowrap">
            <IconClockHour4 size={12} color="var(--text-subtle)" />
            <Text size="xs" c="var(--text-subtle)">{formatTurnaround(test.turnaroundHours)}</Text>
          </Group>
          {locked && <Text size="xs" fw={600} c="var(--success)">Already ordered</Text>}
        </Group>
      </Box>
      <TubeChip tube={test.requiredTubeType} />
      <Text size="sm" fw={600} className="mono" w={64} ta="right">{formatMoney(test.price)}</Text>
    </UnstyledButton>
  );
}

/**
 * Ordering tests (Design.md §5.5): search the catalog or tap a common panel, see tubes, prep and the
 * total before committing. From a consultation the patient is implied; directly, the patient is named.
 */
export function OrderTestsDrawer({ opened, onClose, patient, consultationId, orderedTestIds = [], onOrdered }) {
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('all');
  const [selected, setSelected] = useState(() => new Set());
  const [priority, setPriority] = useState('ROUTINE');
  const [notes, setNotes] = useState('');
  const [debounced] = useDebouncedValue(query, 150);

  const catalog = useLabTests('', { enabled: opened });
  const results = useLabTests(debounced.trim(), { enabled: opened && debounced.trim().length > 0 });
  const order = useOrderTests();

  const locked = useMemo(() => new Set(orderedTestIds), [orderedTestIds]);
  const all = useMemo(() => catalog.data ?? [], [catalog.data]);
  const byId = useMemo(() => new Map(all.map((t) => [t.id, t])), [all]);
  const panels = useMemo(() => availablePanels(all), [all]);
  const categories = useMemo(() => [...new Set(all.map((t) => t.category))], [all]);

  const visible = (debounced.trim() ? results.data ?? [] : all).filter((t) => category === 'all' || t.category === category);
  const picked = [...selected].map((id) => byId.get(id)).filter(Boolean);
  const prep = prepItems(picked);

  const reset = () => {
    setQuery('');
    setCategory('all');
    setSelected(new Set());
    setPriority('ROUTINE');
    setNotes('');
    order.reset();
  };

  const close = () => {
    reset();
    onClose();
  };

  const toggle = (id) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const submit = () => {
    if (picked.length === 0 || order.isPending) return;
    order.mutate(
      {
        ...(consultationId ? { consultationId } : { patientId: patient.id }),
        testIds: picked.map((t) => t.id),
        priority,
        clinicalNotes: notes.trim() || null,
      },
      {
        onSuccess: (created) => {
          notifications.show({
            title: `${created.orderCode} sent to the lab`,
            message: `${picked.length} test${picked.length === 1 ? '' : 's'} ordered for ${created.patient.fullName}.`,
            color: 'teal',
            radius: 'lg',
            icon: <IconFlask size={18} />,
          });
          onOrdered?.(created);
          close();
        },
      },
    );
  };

  useHotkeys([['mod+Enter', () => opened && submit()]], []);

  return (
    <Drawer
      opened={opened}
      onClose={close}
      position="right"
      size={620}
      padding={0}
      title={
        <div>
          <Text fw={600}>Order lab tests</Text>
          <Text size="xs" c="var(--text-muted)">{patient.fullName}{patient.patientCode ? ` · ${patient.patientCode}` : ''}</Text>
        </div>
      }
      styles={{ header: { padding: '16px 20px', borderBottom: '1px solid var(--border)' }, body: { height: 'calc(100% - 69px)' } }}
    >
      <Stack gap={0} h="100%">
        <Stack gap="sm" p="md" pb="sm" style={{ borderBottom: '1px solid var(--border)' }}>
          <TextInput
            data-autofocus
            leftSection={<IconSearch size={16} />}
            placeholder="Search tests — e.g. sugar, thyroid, CBC"
            value={query}
            onChange={(e) => setQuery(e.currentTarget.value)}
          />
          {panels.length > 0 && !query && (
            <Group gap={6}>
              <Text size="xs" c="var(--text-subtle)" fw={600} mr={2}>Panels</Text>
              {panels.map((p) => {
                const ids = p.tests.map((t) => t.id).filter((id) => !locked.has(id));
                const on = ids.length > 0 && ids.every((id) => selected.has(id));
                return (
                  <UnstyledButton key={p.key} className={classes.pill} data-on={on || undefined} onClick={() => setSelected((s) => togglePanel(s, p, locked))}>
                    {p.label}
                    <Text span size="xs" c={on ? 'var(--accent)' : 'var(--text-subtle)'} className="mono" ml={6}>{p.tests.length}</Text>
                  </UnstyledButton>
                );
              })}
            </Group>
          )}
          {categories.length > 1 && (
            <Chip.Group value={category} onChange={setCategory}>
              <Group gap={6}>
                <Chip value="all" size="xs" variant="outline" color="dark">All</Chip>
                {categories.map((c) => (
                  <Chip key={c} value={c} size="xs" variant="outline" color="dark">{c}</Chip>
                ))}
              </Group>
            </Chip.Group>
          )}
        </Stack>

        <ScrollArea style={{ flex: 1 }} px="md" py="xs">
          {catalog.isPending ? (
            <Stack gap="xs" py="sm">{[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} height={52} radius="md" />)}</Stack>
          ) : catalog.isError ? (
            <Text c="var(--critical)" size="sm" py="md">{friendlyMessage(catalog.error)}</Text>
          ) : visible.length === 0 ? (
            <EmptyState icon={IconSearch} title="No matching tests" compact>
              Try another name, or ask the lab to add the test to the catalog.
            </EmptyState>
          ) : (
            groupByCategory(visible).map((group) => (
              <Box key={group.category} mb="sm">
                <Text size="xs" fw={700} tt="uppercase" c="var(--text-subtle)" style={{ letterSpacing: '0.06em' }} px={4} pt="sm" pb={6}>
                  {group.category}
                </Text>
                <Stack gap={2}>
                  {group.items.map((t) => (
                    <TestRow key={t.id} test={t} on={selected.has(t.id)} locked={locked.has(t.id)} onToggle={() => toggle(t.id)} />
                  ))}
                </Stack>
              </Box>
            ))
          )}
        </ScrollArea>

        <Stack gap="sm" p="md" className={classes.footer}>
          {picked.length > 0 && (
            <>
              <Group gap={6}>
                {tubeSummary(picked).map(({ tube, count }) => <TubeChip key={tube} tube={tube} count={count} />)}
                <Text size="xs" c="var(--text-muted)" ml={4}>
                  Results in about {formatTurnaround(slowestTurnaround(picked))}
                </Text>
              </Group>
              {prep.length > 0 && (
                <Box className={classes.prep}>
                  <Group gap={6} mb={4}>
                    <IconSalad size={15} color="var(--warning)" />
                    <Text size="xs" fw={700} c="var(--warning)">The patient needs to prepare</Text>
                  </Group>
                  {prep.map((p) => (
                    <Text key={p.name} size="xs"><b>{p.name}:</b> {p.prep}</Text>
                  ))}
                </Box>
              )}
            </>
          )}
          <Group gap="sm" align="flex-start" wrap="nowrap">
            <SegmentedControl
              size="xs"
              value={priority}
              onChange={setPriority}
              data={[{ label: 'Routine', value: 'ROUTINE' }, { label: 'Urgent', value: 'URGENT' }]}
              color={priority === 'URGENT' ? 'red' : undefined}
            />
            <Textarea
              size="xs"
              placeholder="Note for the lab (optional) — e.g. on warfarin, rule out dengue"
              autosize
              minRows={1}
              maxRows={3}
              maxLength={500}
              value={notes}
              onChange={(e) => setNotes(e.currentTarget.value)}
              style={{ flex: 1 }}
            />
          </Group>
          {order.error && (
            <Alert color="red" variant="light" radius="md" icon={<IconAlertCircle size={16} />}>
              {friendlyMessage(order.error)}
            </Alert>
          )}
          <Group justify="space-between" wrap="nowrap">
            <div>
              <Text size="xs" c="var(--text-muted)">
                {picked.length ? `${picked.length} test${picked.length === 1 ? '' : 's'} selected` : 'Nothing selected yet'}
              </Text>
              <Text fw={600} className="mono" fz={20}>{formatMoney(orderTotal(picked))}</Text>
            </div>
            <Group gap="sm" wrap="nowrap">
              <Text size="xs" c="var(--text-subtle)" visibleFrom="sm"><Kbd size="xs">Ctrl</Kbd> + <Kbd size="xs">Enter</Kbd></Text>
              <Button onClick={submit} disabled={picked.length === 0} loading={order.isPending} leftSection={<IconFlask size={16} />}>
                {picked.length ? `Order ${picked.length} test${picked.length === 1 ? '' : 's'}` : 'Order tests'}
              </Button>
            </Group>
          </Group>
        </Stack>
      </Stack>
    </Drawer>
  );
}
