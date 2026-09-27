'use client';

import { ActionIcon, Badge, Box, Group, Stack, Text, Tooltip } from '@mantine/core';
import { IconBolt, IconClockHour4, IconSalad, IconX } from '@tabler/icons-react';
import { AnimatePresence, motion } from 'motion/react';
import { formatTurnaround, tubeSummary } from '@/lib/lab';
import { formatMoney } from '@/lib/format';
import { TubeChip } from '@/components/ui/TubeChip';

export function OrderCode({ code }) {
  return (
    <Badge size="sm" radius="sm" variant="outline" color="dark" styles={{ root: { textTransform: 'none', fontFamily: 'var(--font-mono)' } }}>
      {code}
    </Badge>
  );
}

export function UrgentBadge() {
  return (
    <Badge size="sm" radius="sm" leftSection={<IconBolt size={12} />} styles={{ root: { textTransform: 'none', background: 'var(--critical-soft)', color: 'var(--critical)', fontWeight: 700 } }}>
      Urgent
    </Badge>
  );
}

/** The tubes an order needs, as chips: "EDTA ×2 · SST". */
export function TubeRow({ items }) {
  return (
    <Group gap={6}>
      {tubeSummary(items).map(({ tube, count }) => <TubeChip key={tube} tube={tube} count={count} />)}
    </Group>
  );
}

/**
 * The tests on an order, one line each: name, tube, turnaround, prep and the price it was ordered at.
 * `onRemove` (doctor, open order) shows a remove button per line.
 */
export function OrderLines({ order, onRemove, removingId, showPrep = true, showTubes = true }) {
  return (
    <Stack gap={0}>
      <AnimatePresence initial={false}>
        {order.items.map((item) => (
          <motion.div
            key={item.id}
            layout
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.18 }}
          >
            <Group wrap="nowrap" gap="sm" py={10} style={{ borderTop: '1px solid var(--border)' }}>
              <Box style={{ flex: 1, minWidth: 0 }}>
                <Group gap={8} wrap="nowrap">
                  <Text size="sm" fw={600} truncate td={item.status === 'CANCELLED' ? 'line-through' : undefined}>{item.name}</Text>
                  <Text size="xs" c="var(--text-subtle)" className="mono">{item.code}</Text>
                </Group>
                <Group gap={6} mt={3} wrap="nowrap">
                  <IconClockHour4 size={12} color="var(--text-subtle)" />
                  <Text size="xs" c="var(--text-subtle)">{formatTurnaround(item.turnaroundHours)}</Text>
                  {showPrep && item.prepInstructions && (
                    <>
                      <Text size="xs" c="var(--text-subtle)">·</Text>
                      <IconSalad size={12} color="var(--warning)" />
                      <Text size="xs" c="var(--warning)" truncate>{item.prepInstructions}</Text>
                    </>
                  )}
                </Group>
              </Box>
              {showTubes && <TubeChip tube={item.tubeType} />}
              <Text size="sm" fw={600} className="mono" w={64} ta="right">{formatMoney(item.price)}</Text>
              {onRemove && (
                <Tooltip label="Remove from order" withArrow>
                  <ActionIcon
                    variant="subtle"
                    color="gray"
                    size="sm"
                    loading={removingId === item.id}
                    onClick={() => onRemove(item)}
                    aria-label={`Remove ${item.name}`}
                  >
                    <IconX size={15} />
                  </ActionIcon>
                </Tooltip>
              )}
            </Group>
          </motion.div>
        ))}
      </AnimatePresence>
      <Group justify="space-between" pt={10} style={{ borderTop: '1px solid var(--border)' }}>
        <Text size="sm" c="var(--text-muted)">{order.items.length} test{order.items.length === 1 ? '' : 's'}</Text>
        <Text fw={700} className="mono">{formatMoney(order.total)}</Text>
      </Group>
    </Stack>
  );
}
