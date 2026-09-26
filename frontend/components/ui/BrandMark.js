import { Text } from '@mantine/core';

/**
 * Wordmark: "CDLMS" with an optional subtitle. `color` overrides the wordmark color, e.g. on the
 * always-dark login panel.
 */
export function BrandMark({ subtitle, color }) {
  return (
    <div style={{ lineHeight: 1.1 }}>
      <Text fw={700} size="lg" c={color} style={{ letterSpacing: '-0.03em' }}>
        CDLMS
      </Text>
      {subtitle && (
        <Text size="xs" c="var(--text-muted)">
          {subtitle}
        </Text>
      )}
    </div>
  );
}
