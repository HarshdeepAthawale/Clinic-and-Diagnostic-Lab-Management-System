'use client';

import { Group, Table, Text } from '@mantine/core';
import { RoleBadge } from '@/components/ui/RoleBadge';
import { Panel } from '@/components/ui/Panel';

/** What each person did in the period, taken from the records they created — never self-reported. */
export function StaffTable({ staff }) {
  return (
    <Panel title="Staff activity" subtitle="Counted from what each person recorded in this period">
      {staff.length === 0 ? (
        <Text size="sm" c="var(--text-muted)" py="xl" ta="center">No staff activity in this period.</Text>
      ) : (
        <Table.ScrollContainer minWidth={560}>
          <Table verticalSpacing="sm">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Person</Table.Th>
                <Table.Th>What they did</Table.Th>
                <Table.Th ta="right">Total</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {staff.map((s) => (
                <Table.Tr key={s.userId}>
                  <Table.Td>
                    <Group gap={8} wrap="nowrap">
                      <Text size="sm" fw={600}>{s.name}</Text>
                      <RoleBadge role={s.role} size="xs" />
                    </Group>
                  </Table.Td>
                  <Table.Td>
                    <Group gap="md" wrap="wrap">
                      {Object.entries(s.measures).map(([measure, count]) => (
                        <Text key={measure} size="xs" c="var(--text-muted)">
                          {measure} <Text span fw={700} c="var(--text)" className="mono">{count}</Text>
                        </Text>
                      ))}
                    </Group>
                  </Table.Td>
                  <Table.Td ta="right"><Text size="sm" fw={700} className="mono">{s.total}</Text></Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}
    </Panel>
  );
}
