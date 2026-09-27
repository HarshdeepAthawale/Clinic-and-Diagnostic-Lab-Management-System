import { Group, Text, UnstyledButton } from '@mantine/core';
import Link from 'next/link';

/**
 * A clickable list row used by several widgets: primary + secondary text, right-hand slot. When the
 * row links somewhere, the right slot sits beside the link rather than inside it, so it can hold its
 * own links or buttons (e.g. a PDF download) without nesting interactive elements.
 */
export function ListRow({ href, title, subtitle, right, leading }) {
  const main = (
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
    </Group>
  );
  if (!href) {
    return (
      <Group wrap="nowrap" gap={0}>
        <div style={{ flex: 1, minWidth: 0 }}>{main}</div>
        {right && <Group gap={6} wrap="nowrap" pr="sm" style={{ flex: 'none' }}>{right}</Group>}
      </Group>
    );
  }
  return (
    <Group wrap="nowrap" gap={0} className="lift" style={{ borderRadius: 'var(--radius-sm)', border: '1px solid transparent' }}>
      <UnstyledButton component={Link} href={href} style={{ display: 'block', flex: 1, minWidth: 0, borderRadius: 'var(--radius-sm)' }}>
        {main}
      </UnstyledButton>
      {right && <Group gap={6} wrap="nowrap" pr="sm" style={{ flex: 'none' }}>{right}</Group>}
    </Group>
  );
}
