import { Badge } from '@mantine/core';

/** Small "Soon · Phase N" tag for features that are planned but not built yet. */
export function PhaseTag({ phase }) {
  return (
    <Badge
      size="xs"
      radius="sm"
      variant="outline"
      styles={{
        root: {
          textTransform: 'none',
          fontWeight: 500,
          color: 'var(--text-subtle)',
          borderColor: 'var(--border-strong)',
        },
      }}
    >
      Phase {String(phase).padStart(2, '0')}
    </Badge>
  );
}
