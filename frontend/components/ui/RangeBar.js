import { Tooltip } from '@mantine/core';

/**
 * Where a lab value sits against its reference range (Design.md §4 "Range bar").
 * The normal band is drawn in the middle; values outside it tint amber, critical ones red.
 */
export function rangeStatus({ value, low, high, criticalLow, criticalHigh }) {
  if ((criticalLow != null && value <= criticalLow) || (criticalHigh != null && value >= criticalHigh)) return 'critical';
  if (value < low) return 'low';
  if (value > high) return 'high';
  return 'normal';
}

export function RangeBar({ value, low, high, criticalLow, criticalHigh, unit = '', width = 140 }) {
  const span = high - low;
  const min = criticalLow ?? low - span * 0.6;
  const max = criticalHigh ?? high + span * 0.6;
  const pct = (v) => Math.min(100, Math.max(0, ((v - min) / (max - min)) * 100));
  const status = rangeStatus({ value, low, high, criticalLow, criticalHigh });
  const markerColor = {
    normal: 'var(--success)',
    low: 'var(--warning)',
    high: 'var(--warning)',
    critical: 'var(--critical)',
  }[status];

  return (
    <Tooltip label={`${value} ${unit} · reference ${low}–${high} ${unit}`}>
      <div
        role="img"
        aria-label={`${value} ${unit}, ${status}, reference range ${low} to ${high}`}
        style={{ position: 'relative', width, height: 10, borderRadius: 5, background: 'var(--surface-2)' }}
      >
        <div
          style={{
            position: 'absolute',
            left: `${pct(low)}%`,
            width: `${pct(high) - pct(low)}%`,
            top: 0,
            bottom: 0,
            borderRadius: 5,
            background: 'color-mix(in srgb, var(--success) 22%, transparent)',
          }}
        />
        <div
          style={{
            position: 'absolute',
            left: `calc(${pct(value)}% - 5px)`,
            top: -2,
            width: 10,
            height: 14,
            borderRadius: 4,
            background: markerColor,
            boxShadow: '0 0 0 2px var(--surface)',
          }}
        />
      </div>
    </Tooltip>
  );
}
