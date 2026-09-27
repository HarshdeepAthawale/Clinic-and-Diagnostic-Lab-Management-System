import { Group, Text } from '@mantine/core';

/**
 * Real vacutainer cap colors so technicians recognise tubes instantly (Design.md §2.1). Sterile
 * containers (urine, stool, swab) are drawn as a white-capped jar or swab tube instead.
 */
export const TUBES = {
  EDTA: { label: 'EDTA', cap: '#9E77ED' },
  PLAIN: { label: 'Plain', cap: '#E5484D' },
  SST: { label: 'SST', cap: '#F5B642' },
  CITRATE: { label: 'Citrate', cap: '#7CC4FA' },
  FLUORIDE: { label: 'Fluoride', cap: '#98A2B3' },
  HEPARIN: { label: 'Heparin', cap: '#3CCB7F' },
  URINE_CUP: { label: 'Urine cup', cap: '#FFFFFF', shape: 'jar' },
  STOOL_CUP: { label: 'Stool cup', cap: '#FFFFFF', shape: 'jar' },
  SWAB_TUBE: { label: 'Swab', cap: '#FFFFFF' },
};

function TubeIcon({ config }) {
  if (config.shape === 'jar') {
    return (
      <svg width="12" height="14" viewBox="0 0 12 14" aria-hidden="true">
        <rect x="0.5" y="0.5" width="11" height="4" rx="1.5" fill={config.cap} stroke="var(--border-strong)" />
        <rect x="1.5" y="5" width="9" height="8.5" rx="2" fill="none" stroke="var(--border-strong)" />
      </svg>
    );
  }
  return (
    <svg width="10" height="14" viewBox="0 0 10 14" aria-hidden="true">
      <rect x="0.5" y="0.5" width="9" height="4.5" rx="1.5" fill={config.cap} stroke={config.cap === '#FFFFFF' ? 'var(--border-strong)' : 'none'} />
      <rect x="1.5" y="5" width="7" height="9" rx="2" fill="none" stroke="var(--border-strong)" />
    </svg>
  );
}

export function TubeChip({ tube, count }) {
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
      <TubeIcon config={config} />
      <Text size="xs" fw={600} style={{ whiteSpace: 'nowrap' }}>
        {config.label}
        {count > 1 && <Text span size="xs" fw={600} c="var(--text-muted)" className="mono"> ×{count}</Text>}
      </Text>
    </Group>
  );
}
