'use client';

import { Alert, Button, Group, Modal, PasswordInput, Stack } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconCheck } from '@tabler/icons-react';
import { useState } from 'react';
import { friendlyMessage } from '@/lib/errors';
import { passwordProblem, useChangePassword } from '@/lib/staff';

function Form({ onClose }) {
  const change = useChangePassword();
  const [values, setValues] = useState({ current: '', next: '', confirm: '' });
  const [problem, setProblem] = useState(null);

  const set = (field) => (event) => {
    const value = event.currentTarget.value;
    setValues((v) => ({ ...v, [field]: value }));
  };

  const submit = (event) => {
    event.preventDefault();
    const found = passwordProblem(values);
    setProblem(found);
    if (found) return;
    change.mutate(
      { current: values.current, next: values.next },
      {
        onSuccess: () => {
          notifications.show({ title: 'Password changed', message: 'Use the new one next time you sign in.', color: 'teal', radius: 'lg', icon: <IconCheck size={18} /> });
          onClose();
        },
      },
    );
  };

  const errorFor = (field) => (problem?.field === field ? problem.message : undefined);
  return (
    <form onSubmit={submit}>
      <Stack gap="sm">
        <PasswordInput label="Current password" autoComplete="current-password" data-autofocus value={values.current} onChange={set('current')} error={errorFor('current')} />
        <PasswordInput label="New password" description="8 to 72 characters" autoComplete="new-password" value={values.next} onChange={set('next')} error={errorFor('next')} />
        <PasswordInput label="New password again" autoComplete="new-password" value={values.confirm} onChange={set('confirm')} error={errorFor('confirm')} />
        {change.isError && (
          <Alert color="red" variant="light" icon={<IconAlertCircle size={16} />}>{friendlyMessage(change.error)}</Alert>
        )}
        <Group justify="flex-end" mt="xs">
          <Button variant="default" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={change.isPending}>Change password</Button>
        </Group>
      </Stack>
    </form>
  );
}

/** Any signed-in person changes their own password; a new staff member replaces the temporary one here. */
export function ChangePasswordModal({ opened, onClose }) {
  return (
    <Modal opened={opened} onClose={onClose} title="Change password" radius="lg" centered>
      {opened && <Form onClose={onClose} />}
    </Modal>
  );
}
