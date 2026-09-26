'use client';

import { Group, Kbd, Modal, Stack, Text } from '@mantine/core';

const SHORTCUTS = [
  { keys: ['Ctrl', 'K'], label: 'Open command palette' },
  { keys: ['['], label: 'Collapse or expand the sidebar' },
  { keys: ['?'], label: 'Show this list' },
  { keys: ['Esc'], label: 'Close dialogs and menus' },
];

/** Keyboard cheatsheet (Design.md §3.4). Screen-specific shortcuts are added as screens are built. */
export function ShortcutsModal({ opened, onClose }) {
  return (
    <Modal opened={opened} onClose={onClose} title={<Text fw={600}>Keyboard shortcuts</Text>} size="md">
      <Stack gap="xs">
        {SHORTCUTS.map(({ keys, label }) => (
          <Group key={label} justify="space-between" py={6} style={{ borderBottom: '1px solid var(--border)' }}>
            <Text size="sm">{label}</Text>
            <Group gap={4}>
              {keys.map((key) => (
                <Kbd key={key} size="sm">
                  {key}
                </Kbd>
              ))}
            </Group>
          </Group>
        ))}
        <Text size="xs" c="var(--text-muted)" mt="xs">
          On a Mac, use ⌘ instead of Ctrl. Shortcuts are paused while you type in a field.
        </Text>
      </Stack>
    </Modal>
  );
}
