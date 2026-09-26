import { Group, Text } from '@mantine/core';

/**
 * Logo: a rounded tile with a pulse line, plus the wordmark.
 * `color` overrides the wordmark color, e.g. on the always-dark login panel.
 */
export function BrandMark({ size = 32, withWordmark = true, subtitle, color }) {
  return (
    <Group gap={10} wrap="nowrap">
      <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="9" fill="var(--accent)" />
        <path
          d="M5 17h5l2.5-6 4 11 3-8 1.8 3H27"
          fill="none"
          stroke="var(--on-ink)"
          strokeWidth="2.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      {withWordmark && (
        <div style={{ lineHeight: 1.1 }}>
          <Text fw={700} size="md" c={color} style={{ letterSpacing: '-0.02em' }}>
            CDLMS
          </Text>
          {subtitle && (
            <Text size="xs" c="var(--text-muted)">
              {subtitle}
            </Text>
          )}
        </div>
      )}
    </Group>
  );
}
