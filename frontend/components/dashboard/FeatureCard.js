'use client';

import { Card, Group, Text, ThemeIcon } from '@mantine/core';
import { motion, useReducedMotion } from 'motion/react';
import { PhaseTag } from '@/components/ui/PhaseTag';

/** A planned capability of this workspace, shown on Phase 01 dashboards. */
export function FeatureCard({ feature, index }) {
  const reduceMotion = useReducedMotion();
  const Icon = feature.icon;

  return (
    <motion.div
      initial={reduceMotion ? false : { opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, delay: 0.08 * index, ease: [0.22, 1, 0.36, 1] }}
      style={{ height: '100%' }}
    >
      <Card h="100%" style={{ background: 'var(--surface)', borderColor: 'var(--border)' }}>
        <Group justify="space-between" align="flex-start" mb="md">
          <ThemeIcon
            size={40}
            radius="md"
            style={{
              background: 'color-mix(in srgb, var(--accent) 12%, transparent)',
              color: 'var(--accent)',
            }}
          >
            <Icon size={21} stroke={1.6} />
          </ThemeIcon>
          <PhaseTag phase={feature.phase} />
        </Group>
        <Text fw={600} mb={6}>
          {feature.title}
        </Text>
        <Text size="sm" c="var(--text-muted)" lh={1.55}>
          {feature.text}
        </Text>
      </Card>
    </motion.div>
  );
}
