import { Box, Group, Text } from '@mantine/core';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { stockFill, stockState } from '@/lib/inventory';

const BAR_COLOR = { out: 'var(--critical)', low: 'var(--warning)', ok: 'var(--ink)', retired: 'var(--border-strong)' };

/** Out of stock, low, or nothing when the level is fine (a healthy row stays quiet). */
export function StockBadge({ item, size = 'sm' }) {
  const state = stockState(item);
  if (state === 'out') return <StatusBadge status="critical" label="Out of stock" size={size} />;
  if (state === 'low') return <StatusBadge status="low-stock" size={size} />;
  if (state === 'retired') return <StatusBadge status="pending" label="Retired" size={size} />;
  return null;
}

/** The level against its threshold: the bar is half full at the threshold, so "low" is visibly the left half. */
export function StockBar({ item, width = 96 }) {
  const fill = stockFill(item);
  if (fill === null) return <Text size="xs" c="var(--text-subtle)">Not watched</Text>;
  return (
    <Box
      w={width}
      h={6}
      role="img"
      aria-label={`${item.currentStock} of ${item.lowStockThreshold} minimum`}
      style={{ borderRadius: 999, background: 'var(--surface-2)', overflow: 'hidden' }}
    >
      <Box
        h="100%"
        style={{
          width: `${fill}%`,
          borderRadius: 999,
          background: BAR_COLOR[stockState(item)],
          transition: 'width var(--duration-base) var(--ease-out)',
        }}
      />
    </Box>
  );
}

/** "42 tubes" with the number in the mono face; turns red when out and amber when low. */
export function StockCount({ item, size = 'sm' }) {
  const state = stockState(item);
  const color = state === 'out' ? 'var(--critical)' : state === 'low' ? 'var(--warning)' : undefined;
  return (
    <Group gap={6} wrap="nowrap" align="baseline">
      <Text size={size} fw={700} className="mono" c={color}>
        {item.currentStock}
      </Text>
      <Text size="xs" c="var(--text-muted)">
        {item.unit}
      </Text>
    </Group>
  );
}
