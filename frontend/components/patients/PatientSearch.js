'use client';

import { Avatar, Group, Loader, Pagination, Stack, Text, TextInput } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconLock, IconSearch, IconUserSearch } from '@tabler/icons-react';
import { useState } from 'react';
import { ageGender } from '@/lib/format';
import { usePatientSearch } from '@/lib/patients';
import { friendlyMessage } from '@/lib/errors';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { ListRow } from '@/components/dashboard/widgets/ListRow';
import { initials } from '@/components/shell/UserMenu';

const PAGE_SIZE = 20;

/**
 * Patient search for doctors and the front desk (ADR-015): summary fields only, phone masked.
 * Doctors see which patients they can open (care relationship).
 */
export function PatientSearch({ basePath, showCare = false }) {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [debounced] = useDebouncedValue(query.trim(), 250);
  const search = usePatientSearch(debounced, page - 1, PAGE_SIZE);
  const results = search.data;
  const totalPages = results ? Math.ceil(results.totalElements / PAGE_SIZE) : 0;

  return (
    <GlowCard p="lg">
      <TextInput
        size="lg"
        radius="lg"
        placeholder="Search by name, phone number or patient ID (PID-…)"
        leftSection={<IconSearch size={20} stroke={1.7} />}
        rightSection={search.isFetching ? <Loader size="xs" /> : null}
        value={query}
        onChange={(e) => {
          setQuery(e.currentTarget.value);
          setPage(1);
        }}
        autoFocus
        aria-label="Search patients"
        mb="md"
      />

      <Text size="xs" c="var(--text-subtle)" mb="sm">
        {debounced ? `${results?.totalElements ?? 0} matching patients` : 'Most recently registered'}
        {showCare && ' · you can open records of patients who have an appointment with you'}
      </Text>

      {search.isError ? (
        <Text c="var(--critical)" size="sm">{friendlyMessage(search.error)}</Text>
      ) : results && results.content.length === 0 ? (
        <EmptyState icon={IconUserSearch} title={debounced ? 'No patients match' : 'No patients yet'} compact>
          {debounced ? 'Check the spelling, or try the phone number or patient ID.' : 'Registered patients will appear here.'}
        </EmptyState>
      ) : (
        <Stack gap={2}>
          {(results?.content ?? []).map((p) => (
            <ListRow
              key={p.id}
              href={`${basePath}/${p.id}`}
              leading={
                <Avatar size={36} radius="xl" styles={{ placeholder: { background: 'var(--surface-2)', color: 'var(--text)', fontSize: 12, fontWeight: 600 } }}>
                  {initials(p.fullName)}
                </Avatar>
              }
              title={p.fullName}
              subtitle={
                <>
                  <span className="mono">{p.patientCode}</span> · {ageGender(p.age, p.gender)} · <span className="mono">{p.maskedPhone}</span>
                </>
              }
              right={
                showCare ? (
                  p.hasCareRelationship ? (
                    <StatusBadge status="verified" label="Can open" />
                  ) : (
                    <Group gap={4} wrap="nowrap">
                      <IconLock size={13} color="var(--text-subtle)" />
                      <Text size="xs" c="var(--text-subtle)">Basic details</Text>
                    </Group>
                  )
                ) : null
              }
            />
          ))}
        </Stack>
      )}

      {totalPages > 1 && (
        <Group justify="center" mt="md">
          <Pagination total={totalPages} value={page} onChange={setPage} radius="md" size="sm" color="dark" />
        </Group>
      )}
    </GlowCard>
  );
}
