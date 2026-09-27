'use client';

import { Badge, Button, Group, SegmentedControl, Skeleton, Stack, Table, Text, TextInput, UnstyledButton } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconPlus, IconSalad, IconSearch } from '@tabler/icons-react';
import { useMemo, useState } from 'react';
import { formatTurnaround, useLabTests } from '@/lib/lab';
import { friendlyMessage } from '@/lib/errors';
import { formatMoney } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { TubeChip } from '@/components/ui/TubeChip';
import { LabTestEditor } from './LabTestEditor';

/** Admin: the lab test catalog — prices, tubes, prep and ranges that ordering and results rely on. */
export function CatalogAdminView() {
  const [query, setQuery] = useState('');
  const [show, setShow] = useState('offered');
  const [editing, setEditing] = useState(null); // null = closed, 'new', or a test id
  const [debounced] = useDebouncedValue(query, 200);
  const tests = useLabTests(debounced.trim(), { includeInactive: true });

  const rows = (tests.data ?? []).filter((t) => (show === 'all' ? true : show === 'offered' ? t.active : !t.active));
  const categories = useMemo(() => [...new Set((tests.data ?? []).map((t) => t.category))].sort(), [tests.data]);
  const offered = (tests.data ?? []).filter((t) => t.active).length;

  return (
    <Stack gap="xl">
      <PageTitle
        title="Test catalog"
        subtitle="What the lab offers. Doctors order from this list; patients see the preparation you write here."
        actions={<Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')}>Add test</Button>}
      />
      <GlowCard p="lg">
        <Group justify="space-between" mb="md" wrap="wrap" gap="sm">
          <TextInput
            leftSection={<IconSearch size={16} />}
            placeholder="Search by name, code or category"
            value={query}
            onChange={(e) => setQuery(e.currentTarget.value)}
            w={{ base: '100%', sm: 320 }}
          />
          <SegmentedControl
            size="xs"
            value={show}
            onChange={setShow}
            data={[
              { label: `Offered${tests.data ? ` · ${offered}` : ''}`, value: 'offered' },
              { label: 'Retired', value: 'retired' },
              { label: 'All', value: 'all' },
            ]}
          />
        </Group>
        {tests.isPending ? (
          <Stack gap="xs">{[0, 1, 2, 3].map((i) => <Skeleton key={i} height={44} radius="md" />)}</Stack>
        ) : tests.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(tests.error)}</Text>
        ) : rows.length === 0 ? (
          <EmptyState icon={IconSearch} title="No tests here" compact>
            {query ? 'Nothing matches that search.' : 'Add the first test to the catalog.'}
          </EmptyState>
        ) : (
          <Table.ScrollContainer minWidth={760}>
            <Table verticalSpacing="sm" highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Test</Table.Th>
                  <Table.Th>Category</Table.Th>
                  <Table.Th>Tube</Table.Th>
                  <Table.Th ta="right">Price</Table.Th>
                  <Table.Th>Turnaround</Table.Th>
                  <Table.Th>Ranges</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {rows.map((t) => (
                  <Table.Tr key={t.id} style={{ cursor: 'pointer', opacity: t.active ? 1 : 0.6 }} onClick={() => setEditing(t.id)}>
                    <Table.Td>
                      <UnstyledButton onClick={() => setEditing(t.id)} style={{ textAlign: 'left' }}>
                        <Group gap={6} wrap="nowrap">
                          <Text size="sm" fw={600}>{t.name}</Text>
                          {t.prepInstructions && <IconSalad size={14} color="var(--warning)" aria-label="Has preparation" />}
                        </Group>
                        <Text size="xs" c="var(--text-subtle)" className="mono">{t.code}</Text>
                      </UnstyledButton>
                    </Table.Td>
                    <Table.Td><Text size="sm">{t.category}</Text></Table.Td>
                    <Table.Td><TubeChip tube={t.requiredTubeType} /></Table.Td>
                    <Table.Td ta="right"><Text size="sm" fw={600} className="mono">{formatMoney(t.price)}</Text></Table.Td>
                    <Table.Td><Text size="sm">{formatTurnaround(t.turnaroundHours)}</Text></Table.Td>
                    <Table.Td><Text size="sm" c="var(--text-muted)">{t.parameterCount || '—'}</Text></Table.Td>
                    <Table.Td>
                      {!t.active && <Badge size="sm" radius="sm" variant="light" color="gray" styles={{ root: { textTransform: 'none' } }}>Retired</Badge>}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </GlowCard>

      <LabTestEditor
        opened={editing !== null}
        testId={editing === 'new' ? null : editing}
        categories={categories}
        onClose={() => setEditing(null)}
      />
    </Stack>
  );
}
