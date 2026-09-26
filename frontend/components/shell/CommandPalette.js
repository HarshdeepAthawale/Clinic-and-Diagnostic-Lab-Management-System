'use client';

import { Spotlight } from '@mantine/spotlight';
import { useDebouncedValue } from '@mantine/hooks';
import { IconKeyboard, IconLock, IconLogout, IconSearch, IconUser } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { api } from '@/lib/api';
import { logout } from '@/lib/auth';

/** Roles whose palette also searches patients, and where a result opens. */
const PATIENT_SEARCH = {
  DOCTOR: '/doctor/patients',
  RECEPTIONIST: '/reception/patients',
};

/** Static actions are matched by label; patient results are already matched by the server. */
function filterActions(query, groups) {
  const q = query.trim().toLowerCase();
  return groups
    .map((group) =>
      group.group === 'Patients'
        ? group
        : { ...group, actions: group.actions.filter((a) => !q || a.label.toLowerCase().includes(q)) },
    )
    .filter((group) => group.actions.length > 0);
}

/**
 * Staff command palette, Ctrl/⌘+K (Design.md §3.3): jump to pages, run actions, and — for doctors
 * and the front desk — find patients by name, phone or patient ID.
 */
export function CommandPalette({ config, onShowShortcuts }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [debounced] = useDebouncedValue(query.trim(), 200);
  const patientBase = PATIENT_SEARCH[config.key];

  const patients = useQuery({
    queryKey: ['patients', 'search', debounced, 'palette'],
    queryFn: ({ signal }) => api(`/patients?${new URLSearchParams({ q: debounced, size: '6' })}`, { signal }),
    enabled: Boolean(patientBase) && debounced.length >= 2,
  });

  const groups = [];
  if (patientBase && debounced.length >= 2 && patients.data?.content.length) {
    groups.push({
      group: 'Patients',
      actions: patients.data.content.map((p) => ({
        id: `patient:${p.id}`,
        label: p.fullName,
        description: `${p.patientCode} · ${p.age} · ${p.maskedPhone}${p.hasCareRelationship === false ? ' · basic details only' : ''}`,
        leftSection: p.hasCareRelationship === false ? <IconLock size={18} stroke={1.6} /> : <IconUser size={18} stroke={1.6} />,
        onClick: () => router.push(`${patientBase}/${p.id}`),
      })),
    });
  }
  groups.push(
    {
      group: 'Go to',
      actions: config.nav
        .filter((item) => !item.phase)
        .map((item) => ({
          id: `nav:${item.href}`,
          label: item.label,
          description: `${config.label} workspace`,
          leftSection: <item.icon size={18} stroke={1.6} />,
          onClick: () => router.push(item.href),
        })),
    },
    {
      group: 'Actions',
      actions: [
        ...(config.cta
          ? [{ id: 'cta', label: config.cta.label, leftSection: <IconUser size={18} stroke={1.6} />, onClick: () => router.push(config.cta.href) }]
          : []),
        { id: 'shortcuts', label: 'Keyboard shortcuts', leftSection: <IconKeyboard size={18} stroke={1.6} />, onClick: onShowShortcuts },
        { id: 'logout', label: 'Sign out', leftSection: <IconLogout size={18} stroke={1.6} />, onClick: () => logout(queryClient) },
      ],
    },
  );

  return (
    <Spotlight
      actions={groups}
      query={query}
      onQueryChange={setQuery}
      filter={filterActions}
      shortcut={['mod + K']}
      nothingFound={patientBase ? 'No patients or pages match.' : 'No pages match.'}
      highlightQuery
      radius="lg"
      searchProps={{
        leftSection: <IconSearch size={18} stroke={1.6} />,
        placeholder: patientBase ? 'Search patients by name, phone or PID — or jump to a page…' : 'Search or jump to…',
      }}
    />
  );
}
