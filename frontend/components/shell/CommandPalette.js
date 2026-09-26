'use client';

import { Spotlight } from '@mantine/spotlight';
import { IconKeyboard, IconLogout, IconSearch } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { logout } from '@/lib/auth';

/**
 * Staff command palette, Ctrl/⌘+K (Design.md §3.3). Phase 01 offers navigation and app actions;
 * patient and sample search join as those features are built.
 */
export function CommandPalette({ config, onShowShortcuts }) {
  const router = useRouter();
  const queryClient = useQueryClient();

  const actions = [
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
        { id: 'shortcuts', label: 'Keyboard shortcuts', leftSection: <IconKeyboard size={18} stroke={1.6} />, onClick: onShowShortcuts },
        { id: 'logout', label: 'Sign out', leftSection: <IconLogout size={18} stroke={1.6} />, onClick: () => logout(queryClient) },
      ],
    },
  ];

  return (
    <Spotlight
      actions={actions}
      shortcut={['mod + K']}
      nothingFound="Nothing found — patient and sample search arrive in later phases."
      highlightQuery
      radius="lg"
      searchProps={{
        leftSection: <IconSearch size={18} stroke={1.6} />,
        placeholder: 'Search or jump to…',
      }}
    />
  );
}
