import { Badge, Tooltip } from '@mantine/core';
import { IconFlask2 } from '@tabler/icons-react';

/**
 * Marks a panel whose numbers come from the demo dataset (lib/demo), so no one mistakes them for
 * real patient data. Removed when the named phase connects the panel to the live API.
 */
export function DemoBadge({ phase }) {
  return (
    <Tooltip label={`Sample data — goes live in Phase ${String(phase).padStart(2, '0')}`} withArrow>
      <Badge
        size="sm"
        radius="sm"
        variant="outline"
        leftSection={<IconFlask2 size={12} />}
        styles={{
          root: {
            textTransform: 'none',
            fontWeight: 600,
            color: 'var(--text-muted)',
            borderColor: 'var(--border-strong)',
            borderStyle: 'dashed',
          },
        }}
      >
        Demo data
      </Badge>
    </Tooltip>
  );
}
