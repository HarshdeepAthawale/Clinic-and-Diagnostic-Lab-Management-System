import { Group, Text } from '@mantine/core';
import { GlowCard } from './GlowCard';

/** Titled dashboard panel: title, optional subtitle, right-side slot (badge/action), body. */
export function Panel({ title, subtitle, right, children, ...props }) {
  return (
    <GlowCard {...props}>
      <Group justify="space-between" align="flex-start" mb="md" wrap="nowrap" gap="sm">
        <div style={{ minWidth: 0 }}>
          <Text component="div" fw={600}>
            {title}
          </Text>
          {subtitle && (
            <Text size="xs" c="var(--text-muted)" mt={2}>
              {subtitle}
            </Text>
          )}
        </div>
        {right && (
          <Group gap={6} wrap="nowrap" style={{ flex: 'none' }}>
            {right}
          </Group>
        )}
      </Group>
      {children}
    </GlowCard>
  );
}
