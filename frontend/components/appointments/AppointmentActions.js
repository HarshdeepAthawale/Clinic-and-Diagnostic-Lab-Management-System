'use client';

import { ActionIcon, Button, Group, Menu, Modal, Stack, Text, Textarea } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconDots } from '@tabler/icons-react';
import { useState } from 'react';
import { useChangeStatus } from '@/lib/appointments';
import { friendlyMessage } from '@/lib/errors';
import { statusActions } from './statusActions';

const DONE_MESSAGES = {
  CHECKED_IN: (a) => `${a.patient.fullName} is checked in${a.queueToken ? ` with token ${a.queueToken}` : ''}.`,
  IN_CONSULTATION: (a) => `${a.patient.fullName} has been called in.`,
  COMPLETED: (a) => `Consultation with ${a.patient.fullName} finished.`,
  NO_SHOW: (a) => `${a.patient.fullName} marked as no-show.`,
  CANCELLED: () => 'Appointment cancelled. The slot is free again.',
};

/**
 * The status buttons a role may use on one appointment: the main move as a button, the rest in a
 * menu. Cancelling asks for an optional reason, which is kept in the appointment's history.
 * `exclude` hides moves that can't work right now (e.g. "Call in" while a patient is already in).
 */
export function AppointmentActions({ role, appointment, size = 'xs', exclude = [] }) {
  const change = useChangeStatus();
  const [asking, setAsking] = useState(null);
  const [note, setNote] = useState('');
  const actions = statusActions(role, appointment).filter((a) => !exclude.includes(a.status));
  if (actions.length === 0) return null;

  const run = (action, reason) =>
    change.mutate(
      { id: appointment.id, status: action.status, note: reason || undefined },
      {
        onSuccess: (updated) => {
          notifications.show({ message: DONE_MESSAGES[action.status](updated), color: 'teal', radius: 'lg' });
          setAsking(null);
          setNote('');
        },
        onError: (error) => notifications.show({ title: "That didn't go through", message: friendlyMessage(error), color: 'red', radius: 'lg' }),
      },
    );
  const choose = (action) => (action.askReason ? setAsking(action) : run(action));

  const primary = actions.find((a) => a.primary);
  const rest = actions.filter((a) => a !== primary);

  return (
    <>
      <Group gap={6} wrap="nowrap" onClick={(e) => e.stopPropagation()}>
        {primary && (
          <Button size={size} radius="md" color="dark" loading={change.isPending && change.variables?.status === primary.status} onClick={() => choose(primary)}>
            {primary.label}
          </Button>
        )}
        {rest.length > 0 && (
          <Menu position="bottom-end" radius="md" shadow="md" withinPortal>
            <Menu.Target>
              <ActionIcon variant="subtle" color="gray" radius="md" size={size === 'xs' ? 30 : 36} aria-label="More actions">
                <IconDots size={16} />
              </ActionIcon>
            </Menu.Target>
            <Menu.Dropdown>
              {rest.map((a) => (
                <Menu.Item key={a.status} color={a.danger ? 'red' : undefined} onClick={() => choose(a)}>
                  {a.label}
                </Menu.Item>
              ))}
            </Menu.Dropdown>
          </Menu>
        )}
      </Group>

      <Modal opened={Boolean(asking)} onClose={() => setAsking(null)} title={<Text fw={600}>{asking?.label}</Text>} radius="lg" centered>
        <Stack>
          <Text size="sm" c="var(--text-muted)">
            {appointment.patient.fullName} with {appointment.doctor.fullName}. The slot becomes free for someone else.
          </Text>
          <Textarea label="Reason (optional)" placeholder="e.g. Patient called to reschedule" maxLength={300} autosize minRows={2} value={note} onChange={(e) => setNote(e.currentTarget.value)} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setAsking(null)}>Keep it</Button>
            <Button color="red" loading={change.isPending} onClick={() => run(asking, note.trim())}>{asking?.label}</Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
