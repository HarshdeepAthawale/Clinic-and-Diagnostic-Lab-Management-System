import { Group, Text } from '@mantine/core';
import { IconAlertOctagon } from '@tabler/icons-react';

/** Allergies and critical values: red, pinned, never dismissible (Design.md §4 "Safety banner"). */
export function SafetyBanner({ title, children }) {
  return (
    <Group
      role="alert"
      gap="sm"
      wrap="nowrap"
      align="flex-start"
      px="md"
      py={10}
      style={{
        borderRadius: 'var(--radius-sm)',
        background: 'var(--critical-soft)',
        border: '1px solid color-mix(in srgb, var(--critical) 30%, transparent)',
        color: 'var(--critical)',
      }}
    >
      <IconAlertOctagon size={18} stroke={2} style={{ flex: 'none', marginTop: 1 }} />
      <Text size="sm" c="var(--critical)">
        <Text span fw={700}>
          {title}
        </Text>{' '}
        {children}
      </Text>
    </Group>
  );
}
