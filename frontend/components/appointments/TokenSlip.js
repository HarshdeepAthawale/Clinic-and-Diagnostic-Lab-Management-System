'use client';

import { Button, Group, Stack, Text } from '@mantine/core';
import { IconPrinter } from '@tabler/icons-react';
import { motion, useReducedMotion } from 'motion/react';
import { formatDateTime } from '@/lib/format';

/** The token a walk-in patient is handed: big number, doctor, time. Printable. */
export function TokenSlip({ appointment, onDone }) {
  const reduceMotion = useReducedMotion();
  return (
    <Stack align="center" gap="md" py="md">
      <Text size="sm" c="var(--text-muted)">Token for {appointment.patient.fullName}</Text>
      <motion.div
        initial={reduceMotion ? false : { scale: 0.85, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: 'spring', stiffness: 260, damping: 20 }}
        style={{
          padding: '18px 36px',
          borderRadius: 20,
          background: 'var(--ink)',
          color: 'var(--on-ink)',
          fontFamily: 'var(--font-mono), monospace',
          fontSize: 56,
          fontWeight: 600,
          letterSpacing: '-0.02em',
        }}
      >
        {appointment.queueToken}
      </motion.div>
      <Text fw={600}>{appointment.doctor.fullName}</Text>
      <Text size="sm" c="var(--text-muted)">{appointment.doctor.specialization} · issued {formatDateTime(appointment.checkedInAt)}</Text>
      <Group>
        <Button variant="default" leftSection={<IconPrinter size={16} />} onClick={() => window.print()}>Print</Button>
        <Button color="dark" onClick={onDone}>Done</Button>
      </Group>
    </Stack>
  );
}
