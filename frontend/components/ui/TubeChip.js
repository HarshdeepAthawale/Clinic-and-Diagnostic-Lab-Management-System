import { Group, Text } from '@mantine/core';

/** Real vacutainer cap colors so technicians recognise tubes instantly (Design.md §2.1). */
export const TUBES = {
  EDTA: { label: 'EDTA', cap: '#9E77ED' },
  PLAIN: { label: 'Plain', cap: '#E5484D' },
  SST: { label: 'SST', cap: '#F5B642' },
  CITRATE: { label: 'Citrate', cap: '#7CC4FA' },
  FLUORIDE: { label: 'Fluoride', cap: '#98A2B3' },
  HEPARIN: { label: 'Heparin', cap: '#3CCB7F' },
};

export function TubeChip({ tube }) {
  const config = TUBES[tube];
  if (!config) return null;
  return (
    <Group
      gap={6}
      wrap="nowrap"
      px={8}
      py={3}
      style={{ display: 'inline-flex', borderRadius: 999, border: '1px solid var(--border)', background: 'var(--surface)' }}
    >
      <svg width="10" height="14" viewBox="0 0 10 14" aria-hidden="true">
        <rect x="0" y="0" width="10" height="5" rx="1.5" fill={config.cap} />
        <rect x="1.5" y="5" width="7" height="9" rx="2" fill="none" stroke="var(--border-strong)" />
      </svg>
      <Text size="xs" fw={600}>
        {config.label}
      </Text>
    </Group>
  );
}
