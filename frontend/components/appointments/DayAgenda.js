'use client';

import { ActionIcon, Box, Button, Group, Stack, Text, Tooltip } from '@mantine/core';
import { IconCalendarOff, IconChevronLeft, IconChevronRight } from '@tabler/icons-react';
import Link from 'next/link';
import { addDays, clinicDate, dayLabel } from '@/lib/appointments';
import { ageGender, APPOINTMENT_STATUS, formatDate, formatTime } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { AppointmentActions } from './AppointmentActions';

/** ‹ Mon 28 Sept › [Today] — steps a "YYYY-MM-DD" date. */
export function DateNav({ date, onChange }) {
  const today = clinicDate();
  return (
    <Group gap={6} wrap="nowrap">
      <ActionIcon variant="default" radius="md" size={36} aria-label="Previous day" onClick={() => onChange(addDays(date, -1))}>
        <IconChevronLeft size={16} />
      </ActionIcon>
      <Text fw={600} miw={150} ta="center">
        {date === today ? 'Today' : dayLabel(date, { weekday: 'long' })}
        <Text span c="var(--text-muted)" fw={500}> · {dayLabel(date, { day: 'numeric', month: 'short' })}</Text>
      </Text>
      <ActionIcon variant="default" radius="md" size={36} aria-label="Next day" onClick={() => onChange(addDays(date, 1))}>
        <IconChevronRight size={16} />
      </ActionIcon>
      {date !== today && (
        <Button variant="subtle" color="dark" size="xs" onClick={() => onChange(today)}>Today</Button>
      )}
    </Group>
  );
}

const TERMINAL = new Set(['CANCELLED', 'NO_SHOW']);

/**
 * Appointments in time order: time, token, patient, doctor, status and the role's actions.
 * Cancelled and no-show visits stay visible but faded. `showDate` is for lists spanning days.
 */
export function DayAgenda({
  appointments,
  role,
  showDoctor = true,
  showDate = false,
  patientHref,
  emptyTitle = 'No appointments this day',
  emptyText = 'Bookings and walk-in tokens for this day appear here.',
}) {
  if (!appointments.length) {
    return (
      <EmptyState icon={IconCalendarOff} title={emptyTitle} compact>
        {emptyText}
      </EmptyState>
    );
  }
  // Only one patient can be with a doctor at a time, so hide "Call in" while someone is.
  const busy = appointments.some((a) => a.status === 'IN_CONSULTATION');
  return (
    <Stack gap={0}>
      {appointments.map((a) => {
        const faded = TERMINAL.has(a.status);
        const href = patientHref?.(a.patient.id);
        return (
          <Group
            key={a.id}
            wrap="nowrap"
            gap="md"
            py={12}
            px={4}
            align="center"
            style={{ borderTop: '1px solid var(--border)', opacity: faded ? 0.55 : 1 }}
          >
            <Box w={showDate ? 96 : 76} style={{ flex: 'none' }}>
              <Text className="mono" size="sm" fw={600} style={{ whiteSpace: 'nowrap', textDecoration: a.status === 'CANCELLED' ? 'line-through' : undefined }}>
                {formatTime(a.scheduledAt)}
              </Text>
              <Text size="xs" c="var(--text-subtle)" style={{ whiteSpace: 'nowrap' }}>
                {showDate ? formatDate(a.scheduledAt) : a.kind === 'WALK_IN' ? 'Walk-in' : `${a.durationMinutes} min`}
              </Text>
            </Box>
            <Box w={60} style={{ flex: 'none' }} visibleFrom="sm">
              {a.queueToken && (
                <Text className="mono" size="sm" fw={600} ta="center" style={{ border: '1px solid var(--border)', borderRadius: 8, padding: '2px 0' }}>
                  {a.queueToken}
                </Text>
              )}
            </Box>
            <Box style={{ flex: 1, minWidth: 0 }}>
              <Text size="sm" fw={600} truncate component={href ? Link : 'p'} href={href} style={{ color: 'inherit', textDecoration: 'none' }}>
                {a.patient.fullName}
                <Text span size="xs" c="var(--text-muted)" fw={400}> · {ageGender(a.patient.age, a.patient.gender)} · {a.patient.patientCode}</Text>
              </Text>
              <Text size="xs" c="var(--text-muted)" truncate>
                {[showDoctor ? a.doctor.fullName : null, a.reason, a.cancellationReason && `Cancelled: ${a.cancellationReason}`].filter(Boolean).join(' · ') || 'No reason given'}
              </Text>
            </Box>
            <StatusBadge status={APPOINTMENT_STATUS[a.status]} label={a.status === 'CANCELLED' ? 'Cancelled' : undefined} />
            <Box miw={role === 'PATIENT' ? 0 : 110} style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <AppointmentActions role={role} appointment={a} exclude={busy ? ['IN_CONSULTATION'] : []} />
            </Box>
          </Group>
        );
      })}
    </Stack>
  );
}

/** Small counts row: "12 booked · 3 waiting · 5 done …" */
export function StatusSummary({ appointments }) {
  const count = (s) => appointments.filter((a) => a.status === s).length;
  const items = [
    ['Booked', count('BOOKED')],
    ['Waiting', count('CHECKED_IN')],
    ['With doctor', count('IN_CONSULTATION')],
    ['Done', count('COMPLETED')],
    ['No-show', count('NO_SHOW')],
    ['Cancelled', count('CANCELLED')],
  ];
  return (
    <Group gap="lg" wrap="wrap">
      {items.map(([label, n]) => (
        <Tooltip key={label} label={`${n} ${label.toLowerCase()}`} withArrow>
          <Group gap={6} wrap="nowrap">
            <Text className="mono" fw={600} size="sm">{n}</Text>
            <Text size="sm" c="var(--text-muted)">{label}</Text>
          </Group>
        </Tooltip>
      ))}
    </Group>
  );
}
