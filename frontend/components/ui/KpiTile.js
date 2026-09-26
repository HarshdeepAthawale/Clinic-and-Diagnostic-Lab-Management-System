'use client';

import { Group, Text, ThemeIcon } from '@mantine/core';
import { IconArrowDownRight, IconArrowUpRight } from '@tabler/icons-react';
import { animate, useReducedMotion } from 'motion/react';
import { useEffect, useState } from 'react';
import { GlowCard } from './GlowCard';
import { Sparkline } from './Sparkline';

function CountUp({ value, format }) {
  const reduceMotion = useReducedMotion();
  const [shown, setShown] = useState(reduceMotion ? value : 0);
  useEffect(() => {
    if (reduceMotion) return undefined;
    const controls = animate(0, value, { duration: 0.9, ease: [0.22, 1, 0.36, 1], onUpdate: setShown });
    return () => controls.stop();
  }, [value, reduceMotion]);
  return format(reduceMotion ? value : shown);
}

/**
 * KPI tile: label, big number (counts up on first load), change vs. previous period, sparkline.
 * `goodWhenUp` decides whether a rise is shown green or amber (e.g. turnaround time: lower is better).
 */
export function KpiTile({
  label,
  value,
  format = (v) => Math.round(v).toLocaleString('en-IN'),
  suffix,
  delta,
  deltaLabel = 'vs last week',
  caption,
  goodWhenUp = true,
  trend,
  icon: Icon,
  tone = 'var(--ink)',
}) {
  const up = delta >= 0;
  const good = up === goodWhenUp;
  return (
    <GlowCard>
      <Group justify="space-between" align="flex-start" mb={10} wrap="nowrap">
        <Text size="sm" c="var(--text-muted)" fw={500}>
          {label}
        </Text>
        {Icon && (
          <ThemeIcon size={30} radius="md" style={{ background: 'var(--surface-2)', color: tone }}>
            <Icon size={17} stroke={1.7} />
          </ThemeIcon>
        )}
      </Group>
      <Group justify="space-between" align="flex-end" wrap="nowrap" gap="xs">
        <div>
          <Text fz={28} fw={700} lh={1.1} className="mono" style={{ letterSpacing: '-0.02em' }}>
            <CountUp value={value} format={format} />
            {suffix && (
              <Text span fz={15} fw={600} c="var(--text-muted)" ml={3}>
                {suffix}
              </Text>
            )}
          </Text>
          {caption && delta == null && (
            <Text size="xs" c="var(--text-subtle)" mt={6}>
              {caption}
            </Text>
          )}
          {delta != null && (
            <Group gap={4} mt={6} wrap="nowrap">
              {up ? (
                <IconArrowUpRight size={14} color={good ? 'var(--success)' : 'var(--warning)'} />
              ) : (
                <IconArrowDownRight size={14} color={good ? 'var(--success)' : 'var(--warning)'} />
              )}
              <Text size="xs" fw={600} c={good ? 'var(--success)' : 'var(--warning)'}>
                {Math.abs(delta)}%
              </Text>
              <Text size="xs" c="var(--text-subtle)">
                {deltaLabel}
              </Text>
            </Group>
          )}
        </div>
        {trend && <Sparkline values={trend} color={tone} label={`${label} trend`} />}
      </Group>
    </GlowCard>
  );
}
