import { Badge } from '@mantine/core';
import { roleConfig } from '@/lib/roles';

/** Neutral role pill — one accent in the whole UI, so roles are told apart by name, not color. */
export function RoleBadge({ role, size = 'sm' }) {
  const config = roleConfig(role);
  if (!config) return null;
  return (
    <Badge
      size={size}
      radius="xl"
      styles={{
        root: {
          color: 'var(--text)',
          background: 'var(--surface-2)',
          border: '1px solid var(--border)',
          textTransform: 'none',
          fontWeight: 600,
        },
      }}
    >
      {config.label}
    </Badge>
  );
}
