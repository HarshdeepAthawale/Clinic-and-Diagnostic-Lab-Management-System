'use client';

import { Group, Pagination, ScrollArea, Skeleton, Stack, Table, Text, TextInput } from '@mantine/core';
import { IconFileSearch } from '@tabler/icons-react';
import { useState } from 'react';
import { friendlyMessage } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import { useAccessLog } from '@/lib/patients';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageTitle } from '@/components/ui/PageTitle';
import { Panel } from '@/components/ui/Panel';
import { RoleBadge } from '@/components/ui/RoleBadge';

const PAGE_SIZE = 25;
const RESOURCE = { EMR: 'Full record', CONSULTATIONS: 'Consultations', PRESCRIPTION: 'Prescription', LAB_REPORT: 'Lab report', LAB_HISTORY: 'Lab history' };

/** Admin: every full-record open, newest first, filterable by date (Security.md §6). */
export function AccessLogView() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(1);
  const filters = {
    from: from ? new Date(`${from}T00:00:00`).toISOString() : '',
    to: to ? new Date(`${to}T23:59:59`).toISOString() : '',
  };
  const log = useAccessLog(filters, page - 1, PAGE_SIZE);
  const totalPages = log.data ? Math.ceil(log.data.totalElements / PAGE_SIZE) : 0;

  return (
    <Stack gap="xl">
      <PageTitle title="Record access log" subtitle="Who opened which patient record, and when. Entries can never be edited or deleted." />
      <Panel
        title={log.data ? `${log.data.totalElements} entries` : 'Entries'}
        right={
          <Group gap="xs" wrap="nowrap">
            <TextInput type="date" size="xs" value={from} onChange={(e) => { setFrom(e.currentTarget.value); setPage(1); }} aria-label="From date" />
            <Text size="xs" c="var(--text-muted)">to</Text>
            <TextInput type="date" size="xs" value={to} onChange={(e) => { setTo(e.currentTarget.value); setPage(1); }} aria-label="To date" />
          </Group>
        }
      >
        {log.isPending ? (
          <Stack gap="xs">{[0, 1, 2, 3].map((i) => <Skeleton key={i} height={36} radius="md" />)}</Stack>
        ) : log.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(log.error)}</Text>
        ) : log.data.content.length === 0 ? (
          <EmptyState icon={IconFileSearch} title="No entries" compact>
            Record opens by doctors appear here the moment they happen.
          </EmptyState>
        ) : (
          <ScrollArea>
            <Table verticalSpacing={10} highlightOnHover miw={640}>
              <Table.Thead>
                <Table.Tr>
                  {['When', 'Staff member', 'Patient', 'Opened'].map((h) => (
                    <Table.Th key={h} fz="xs" c="var(--text-subtle)" tt="uppercase" style={{ letterSpacing: '0.05em' }}>{h}</Table.Th>
                  ))}
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {log.data.content.map((row) => (
                  <Table.Tr key={row.id}>
                    <Table.Td><Text size="sm" className="mono">{formatDateTime(row.accessedAt)}</Text></Table.Td>
                    <Table.Td>
                      <Group gap="xs" wrap="nowrap">
                        <Text size="sm" fw={600}>{row.userName}</Text>
                        <RoleBadge role={row.userRole} size="xs" />
                      </Group>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm" fw={600}>{row.patientName}</Text>
                      <Text size="xs" c="var(--text-muted)" className="mono">{row.patientCode}</Text>
                    </Table.Td>
                    <Table.Td><Text size="sm">{RESOURCE[row.resource] ?? row.resource}</Text></Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </ScrollArea>
        )}
        {totalPages > 1 && (
          <Group justify="center" mt="md">
            <Pagination total={totalPages} value={page} onChange={setPage} size="sm" color="dark" />
          </Group>
        )}
      </Panel>
    </Stack>
  );
}
