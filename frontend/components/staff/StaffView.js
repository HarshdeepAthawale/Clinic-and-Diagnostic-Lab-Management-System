'use client';

import { Button, Group, Modal, Skeleton, Stack, Switch, Table, Text, TextInput } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { IconPlus, IconSearch, IconUsers } from '@tabler/icons-react';
import { useState } from 'react';
import { friendlyMessage } from '@/lib/errors';
import { formatDate } from '@/lib/format';
import { useMe } from '@/lib/auth';
import { useSetStaffActive, useStaff } from '@/lib/staff';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { RoleBadge } from '@/components/ui/RoleBadge';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { StaffEditor } from './StaffEditor';

/** Admin: who works here, and who can sign in. Accounts are switched off, never deleted, so their history stays theirs. */
export function StaffView() {
  const meId = useMe().data?.id;
  const [query, setQuery] = useState('');
  const [showInactive, setShowInactive] = useState(true);
  const [adding, setAdding] = useState(false);
  const [debounced] = useDebouncedValue(query, 200);
  const staff = useStaff(debounced.trim(), showInactive);
  const setActive = useSetStaffActive();

  const [confirming, setConfirming] = useState(null);

  const confirm = () => {
    const person = confirming;
    const off = person.active;
    setActive.mutate(
      { userId: person.userId, active: !off },
      {
        onSuccess: () => notifications.show({ title: off ? 'Account deactivated' : 'Account reactivated', message: person.fullName, color: 'teal', radius: 'lg' }),
        onError: (error) => notifications.show({ title: "Couldn't change the account", message: friendlyMessage(error), color: 'red', radius: 'lg' }),
        onSettled: () => setConfirming(null),
      },
    );
  };

  return (
    <Stack gap="xl">
      <PageTitle
        title="Staff"
        subtitle="Everyone who works here. Add an account, or switch one off when someone leaves."
        actions={<Button leftSection={<IconPlus size={16} />} onClick={() => setAdding(true)}>Add account</Button>}
      />
      <GlowCard p="lg">
        <Group justify="space-between" mb="md" wrap="wrap" gap="sm">
          <TextInput leftSection={<IconSearch size={16} />} placeholder="Search by name or email" value={query} onChange={(e) => setQuery(e.currentTarget.value)} w={{ base: '100%', sm: 300 }} aria-label="Search staff" />
          <Switch size="xs" label="Show deactivated" checked={showInactive} onChange={(e) => setShowInactive(e.currentTarget.checked)} />
        </Group>
        {staff.isPending ? (
          <Stack gap="xs">{[0, 1, 2, 3].map((i) => <Skeleton key={i} height={48} radius="md" />)}</Stack>
        ) : staff.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(staff.error)}</Text>
        ) : staff.data.length === 0 ? (
          <EmptyState icon={IconUsers} title="No one here" compact>{query ? 'Nothing matches that search.' : 'Add the first account.'}</EmptyState>
        ) : (
          <Table.ScrollContainer minWidth={640}>
            <Table verticalSpacing="sm" highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Name</Table.Th>
                  <Table.Th>Role</Table.Th>
                  <Table.Th>Details</Table.Th>
                  <Table.Th>Added</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {staff.data.map((p) => (
                  <Table.Tr key={p.userId} style={{ opacity: p.active ? 1 : 0.6 }}>
                    <Table.Td>
                      <Text size="sm" fw={600}>{p.fullName}</Text>
                      <Text size="xs" c="var(--text-subtle)">{p.email}</Text>
                    </Table.Td>
                    <Table.Td><RoleBadge role={p.role} size="sm" /></Table.Td>
                    <Table.Td><Text size="sm" c="var(--text-muted)">{p.detail || '—'}</Text></Table.Td>
                    <Table.Td><Text size="sm" c="var(--text-muted)">{formatDate(p.createdAt)}</Text></Table.Td>
                    <Table.Td ta="right">
                      <Group gap="sm" justify="flex-end" wrap="nowrap">
                        {!p.active && <StatusBadge status="pending" label="Deactivated" size="xs" />}
                        {p.userId === meId ? (
                          <Text size="xs" c="var(--text-subtle)">You</Text>
                        ) : (
                          <Button size="compact-xs" variant="subtle" color={p.active ? 'red' : 'dark'} onClick={() => setConfirming(p)}>
                            {p.active ? 'Deactivate' : 'Reactivate'}
                          </Button>
                        )}
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </GlowCard>
      <StaffEditor opened={adding} onClose={() => setAdding(false)} />
      <Modal opened={Boolean(confirming)} onClose={() => setConfirming(null)} radius="lg" centered
        title={confirming?.active ? `Deactivate ${confirming?.fullName}?` : `Reactivate ${confirming?.fullName}?`}>
        <Stack gap="md">
          <Text size="sm" c="var(--text-muted)">
            {confirming?.active
              ? "They are signed out straight away and can't sign in again until you reactivate them. Everything they recorded stays on file."
              : 'They will be able to sign in again with their current password.'}
          </Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setConfirming(null)}>Cancel</Button>
            <Button color={confirming?.active ? 'red' : 'dark'} loading={setActive.isPending} onClick={confirm}>
              {confirming?.active ? 'Deactivate' : 'Reactivate'}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
