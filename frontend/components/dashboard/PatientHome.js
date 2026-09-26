'use client';

import { Badge, Card, Group, SimpleGrid, Skeleton, Stack, Text } from '@mantine/core';
import { IconCalendarEvent, IconFileText, IconReceipt, IconTimeline } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { motion, useReducedMotion } from 'motion/react';
import { api } from '@/lib/api';
import { useMe } from '@/lib/auth';
import { EmptyState } from '@/components/ui/EmptyState';
import { SampleJourney } from '@/components/ui/SampleJourney';
import { greeting, longDate, shortName } from './greeting';

const CARDS = [
  { icon: IconFileText, title: 'No new results', text: "When a report is verified, you'll see it here first." },
  { icon: IconCalendarEvent, title: 'No upcoming appointments', text: 'Booked visits and their prep instructions will show here.' },
  { icon: IconReceipt, title: 'No bills to pay', text: "You're all settled up." },
];

/** Patient home (Design.md §5.6): warm greeting, "what matters now" cards, sample journey preview. */
export function PatientHome() {
  const { data: me } = useMe();
  const reduceMotion = useReducedMotion();
  // Confirms the patient area is reachable with this session (Phase 01 exit criterion).
  useQuery({ queryKey: ['dashboard', 'PATIENT'], queryFn: ({ signal }) => api('/dashboard/patient', { signal }) });

  const rise = (index) => ({
    initial: reduceMotion ? false : { opacity: 0, y: 10 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: 0.35, delay: 0.07 * index, ease: [0.22, 1, 0.36, 1] },
  });

  return (
    <Stack gap={28}>
      <div>
        <Text size="sm" c="var(--text-muted)" fw={500} mb={6}>
          {longDate()}
        </Text>
        {me ? (
          <h1 className="serif" style={{ fontSize: 'clamp(34px, 5vw, 46px)', lineHeight: 1.05, margin: 0 }}>
            {greeting()}, {shortName(me.name)}.
          </h1>
        ) : (
          <Skeleton height={44} width={300} radius="md" />
        )}
        <Text c="var(--text-muted)" mt={8} size="md">
          Here&apos;s everything about your care, in one place.
        </Text>
      </div>

      <motion.div {...rise(0)}>
        <Card style={{ background: 'var(--surface)' }} padding="xl">
          <Group justify="space-between" mb="lg" wrap="nowrap" align="flex-start">
            <Group gap="sm" wrap="nowrap">
              <IconTimeline size={22} color="var(--brand)" stroke={1.7} style={{ flex: 'none' }} />
              <div>
                <Text fw={600}>Your lab tests, tracked live</Text>
                <Text size="sm" c="var(--text-muted)">
                  Every test you take will show its journey like this, from collection to your report.
                </Text>
              </div>
            </Group>
            <Badge variant="light" radius="sm" style={{ textTransform: 'none', flex: 'none' }}>
              Preview
            </Badge>
          </Group>
          <SampleJourney
            current={3}
            times={['9:02 AM', '9:40 AM', '10:15 AM']}
            currentNote="Now"
            label="Example sample progress"
          />
        </Card>
      </motion.div>

      <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="lg">
        {CARDS.map((card, index) => (
          <motion.div key={card.title} {...rise(index + 1)}>
            <Card h="100%" style={{ background: 'var(--surface)' }}>
              <EmptyState icon={card.icon} title={card.title} compact>
                {card.text}
              </EmptyState>
            </Card>
          </motion.div>
        ))}
      </SimpleGrid>
    </Stack>
  );
}
