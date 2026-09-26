import { Stack, Text, ThemeIcon } from '@mantine/core';

/** Icon + one sentence + optional action (Design.md §4 "Empty states"). */
export function EmptyState({ icon: Icon, title, children, action, compact = false }) {
  return (
    <Stack align="center" gap={compact ? 6 : 10} py={compact ? 'md' : 'xl'} ta="center">
      {Icon && (
        <ThemeIcon
          size={compact ? 40 : 52}
          radius="xl"
          variant="light"
          style={{ background: 'var(--surface-2)', color: 'var(--text-muted)' }}
        >
          <Icon size={compact ? 20 : 26} stroke={1.5} />
        </ThemeIcon>
      )}
      <Text fw={600} size={compact ? 'sm' : 'md'}>
        {title}
      </Text>
      {children && (
        <Text size="sm" c="var(--text-muted)" maw={360}>
          {children}
        </Text>
      )}
      {action}
    </Stack>
  );
}
