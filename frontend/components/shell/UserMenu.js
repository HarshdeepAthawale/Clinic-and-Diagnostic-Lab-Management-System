'use client';

import { Avatar, Group, Menu, Stack, Text, UnstyledButton } from '@mantine/core';
import { IconChevronDown, IconKeyboard, IconKey, IconLogout } from '@tabler/icons-react';
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { logout } from '@/lib/auth';
import { RoleBadge } from '@/components/ui/RoleBadge';
import { ChangePasswordModal } from './ChangePasswordModal';

export function initials(name = '') {
  const parts = name
    .replace(/^Dr\.?\s+/i, '')
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  return (
    ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase() ||
    '?'
  );
}

/** Initials avatar; menu with identity, role, shortcuts and sign-out. */
/** `inverted` renders a white avatar for dark backgrounds such as the navbar. */
export function UserMenu({ me, onShowShortcuts, compact = false, inverted = false }) {
  const queryClient = useQueryClient();
  const [changingPassword, setChangingPassword] = useState(false);

  return (
    <>
      <Menu position="bottom-end" width={260} shadow="md" radius="lg" withinPortal>
        <Menu.Target>
          <UnstyledButton aria-label="Account menu" style={{ borderRadius: 999, padding: 2 }}>
            <Group gap={8} wrap="nowrap">
              <Avatar
                radius="xl"
                size={34}
                styles={{
                  placeholder: inverted
                    ? { background: '#fff', color: 'var(--ink)', fontWeight: 600, fontSize: 13 }
                    : {
                        background: 'var(--ink)',
                        color: 'var(--on-ink)',
                        fontWeight: 600,
                        fontSize: 13,
                      },
                }}
              >
                {initials(me.name)}
              </Avatar>
              {!compact && (
                <>
                  <Text size="sm" fw={600} visibleFrom="md" maw={140} truncate>
                    {me.name}
                  </Text>
                  <IconChevronDown size={14} color="var(--text-muted)" />
                </>
              )}
            </Group>
          </UnstyledButton>
        </Menu.Target>
        <Menu.Dropdown>
          <Stack gap={4} p="sm">
            <Text fw={600} size="sm" truncate>
              {me.name}
            </Text>
            <Text size="xs" c="var(--text-muted)" truncate>
              {me.email}
            </Text>
            <div>
              <RoleBadge role={me.role} size="sm" />
            </div>
          </Stack>
          <Menu.Divider />
          {onShowShortcuts && (
            <Menu.Item
              leftSection={<IconKeyboard size={16} />}
              rightSection={
                <Text size="xs" c="dimmed">
                  ?
                </Text>
              }
              onClick={onShowShortcuts}
            >
              Keyboard shortcuts
            </Menu.Item>
          )}
          <Menu.Item leftSection={<IconKey size={16} />} onClick={() => setChangingPassword(true)}>
            Change password
          </Menu.Item>
          <Menu.Item
            color="red"
            leftSection={<IconLogout size={16} />}
            onClick={() => logout(queryClient)}
          >
            Sign out
          </Menu.Item>
        </Menu.Dropdown>
      </Menu>
      <ChangePasswordModal opened={changingPassword} onClose={() => setChangingPassword(false)} />
    </>
  );
}
