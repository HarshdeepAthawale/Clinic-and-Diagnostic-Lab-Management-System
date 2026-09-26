import { Badge } from '@mantine/core';
import { roleConfig } from '@/lib/roles';

/** Role pill tinted with the role's wayfinding accent. */
export function RoleBadge({ role, size = 'sm' }) {
  const config = roleConfig(role);
  if (!config) return null;
  return (
    <Badge
      size={size}
      radius="xl"
      variant="light"
      styles={{
        root: {
          color: config.accentVar,
          background: `color-mix(in srgb, ${config.accentVar} 12%, transparent)`,
          textTransform: 'none',
          fontWeight: 600,
        },
      }}
    >
      {config.label}
    </Badge>
  );
}
