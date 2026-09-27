'use client';

import { ActionIcon, Alert, Button, Group, Select, Stack, Switch, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconPlus, IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { useSaveWorkingHours } from '@/lib/appointments';
import { friendlyMessage } from '@/lib/errors';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const hhmm = (t) => t.slice(0, 5);

/** Server blocks → { 1: [{start, end}], … } plus one slot length for the whole week. */
function toState(blocks) {
  const week = Object.fromEntries(DAYS.map((_, i) => [i + 1, []]));
  blocks.forEach((b) => week[b.dayOfWeek].push({ start: hhmm(b.startTime), end: hhmm(b.endTime) }));
  return { week, slotMinutes: String(blocks[0]?.slotMinutes ?? 15) };
}

/**
 * Weekly working hours: each day on/off with one or more blocks (morning and evening clinics).
 * Changing hours never moves or cancels existing bookings.
 */
export function WorkingHoursEditor({ doctorId, blocks }) {
  const save = useSaveWorkingHours(doctorId);
  const [{ week, slotMinutes }, setState] = useState(() => toState(blocks));
  const setWeek = (fn) => setState((s) => ({ ...s, week: fn(s.week) }));

  const setBlock = (day, index, field, value) =>
    setWeek((w) => ({ ...w, [day]: w[day].map((b, i) => (i === index ? { ...b, [field]: value } : b)) }));
  const toggleDay = (day, on) => setWeek((w) => ({ ...w, [day]: on ? [{ start: '09:00', end: '13:00' }] : [] }));
  const addBlock = (day) => setWeek((w) => ({ ...w, [day]: [...w[day], { start: '16:00', end: '19:00' }] }));
  const removeBlock = (day, index) => setWeek((w) => ({ ...w, [day]: w[day].filter((_, i) => i !== index) }));

  const submit = () => {
    const payload = Object.entries(week).flatMap(([day, list]) =>
      list.map((b) => ({ dayOfWeek: Number(day), startTime: b.start, endTime: b.end, slotMinutes: Number(slotMinutes) })),
    );
    save.mutate(payload, {
      onSuccess: () => notifications.show({ message: 'Working hours saved. New bookings follow them from now on.', color: 'teal', radius: 'lg' }),
    });
  };

  return (
    <Stack gap="md">
      <Group justify="space-between" align="flex-end">
        <Text size="sm" c="var(--text-muted)" maw={420}>
          Patients and the front desk can book any free slot inside these hours. Existing bookings stay as they are.
        </Text>
        <Select
          label="Slot length"
          w={140}
          radius="md"
          data={['10', '15', '20', '30', '45', '60'].map((v) => ({ value: v, label: `${v} minutes` }))}
          value={slotMinutes}
          onChange={(v) => setState((s) => ({ ...s, slotMinutes: v }))}
          allowDeselect={false}
        />
      </Group>

      <Stack gap={0}>
        {DAYS.map((name, i) => {
          const day = i + 1;
          const list = week[day];
          return (
            <Group key={day} py={10} gap="md" align="flex-start" wrap="nowrap" style={{ borderTop: '1px solid var(--border)' }}>
              <Switch w={130} mt={8} label={name} checked={list.length > 0} onChange={(e) => toggleDay(day, e.currentTarget.checked)} color="dark" />
              {list.length === 0 ? (
                <Text size="sm" c="var(--text-subtle)" mt={8}>Not seeing patients</Text>
              ) : (
                <Stack gap={6} style={{ flex: 1 }}>
                  {list.map((b, index) => (
                    <Group key={index} gap={8} wrap="nowrap">
                      <TextInput type="time" size="sm" radius="md" w={120} value={b.start} onChange={(e) => setBlock(day, index, 'start', e.currentTarget.value)} aria-label={`${name} start`} />
                      <Text size="sm" c="var(--text-muted)">to</Text>
                      <TextInput type="time" size="sm" radius="md" w={120} value={b.end} onChange={(e) => setBlock(day, index, 'end', e.currentTarget.value)} aria-label={`${name} end`} />
                      <ActionIcon variant="subtle" color="gray" onClick={() => removeBlock(day, index)} aria-label="Remove hours">
                        <IconTrash size={15} />
                      </ActionIcon>
                      {index === list.length - 1 && (
                        <Button variant="subtle" color="dark" size="compact-sm" leftSection={<IconPlus size={14} />} onClick={() => addBlock(day)}>
                          Add hours
                        </Button>
                      )}
                    </Group>
                  ))}
                </Stack>
              )}
            </Group>
          );
        })}
      </Stack>

      {save.isError && (
        <Alert color="red" variant="light" radius="md" icon={<IconAlertCircle size={18} />}>{friendlyMessage(save.error)}</Alert>
      )}
      <Group justify="flex-end">
        <Button color="dark" loading={save.isPending} onClick={submit}>Save working hours</Button>
      </Group>
    </Stack>
  );
}
