import { Group, Text, UnstyledButton } from '@mantine/core';
import Link from 'next/link';

/** A clickable list row used by several widgets: primary + secondary text, right-hand slot. */
export function ListRow({ href, title, subtitle, right, leading }) {
  const body = (
    <Group wrap="nowrap" gap="md" py={10} px="sm">
      {leading}
      <div style={{ flex: 1, minWidth: 0 }}>
        <Text size="sm" fw={600} truncate>
          {title}
        </Text>
        {subtitle && (
          <Text size="xs" c="var(--text-muted)" truncate>
            {subtitle}
          </Text>
        )}
      </div>
      {right}
    </Group>
  );
  if (!href) return body;
  return (
    <UnstyledButton component={Link} href={href} className="lift" style={{ display: 'block', borderRadius: 'var(--radius-sm)', border: '1px solid transparent' }}>
      {body}
    </UnstyledButton>
  );
}
