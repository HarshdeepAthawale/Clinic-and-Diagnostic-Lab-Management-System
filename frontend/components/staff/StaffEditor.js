'use client';

import { Alert, Box, Button, CopyButton, Group, Modal, Select, Stack, Text, TextInput } from '@mantine/core';
import { IconAlertCircle, IconCheck, IconCopy } from '@tabler/icons-react';
import { useState } from 'react';
import { friendlyMessage } from '@/lib/errors';
import { createBody, STAFF_ROLES, staffProblems, useCreateStaff } from '@/lib/staff';

const blank = { role: 'RECEPTIONIST', fullName: '', email: '', specialization: '', qualification: '', registrationNumber: '' };

/** After creating: the temporary password, once. It is not stored in the clear and can't be shown again. */
function Created({ result, onClose }) {
  return (
    <Stack gap="md">
      <div>
        <Text fw={600}>{result.staff.fullName} can now sign in</Text>
        <Text size="sm" c="var(--text-muted)">{result.staff.email}</Text>
      </div>
      <Box p="md" style={{ borderRadius: 'var(--radius-md)', border: '1px dashed var(--border-strong)', background: 'var(--surface-alt)' }}>
        <Text size="xs" c="var(--text-muted)" tt="uppercase" style={{ letterSpacing: '0.06em' }}>Temporary password</Text>
        <Group justify="space-between" wrap="nowrap" mt={4}>
          <Text fz={22} fw={600} className="mono" style={{ letterSpacing: '0.04em' }}>{result.temporaryPassword}</Text>
          <CopyButton value={result.temporaryPassword}>
            {({ copied, copy }) => (
              <Button size="compact-sm" variant="default" leftSection={copied ? <IconCheck size={14} /> : <IconCopy size={14} />} onClick={copy}>
                {copied ? 'Copied' : 'Copy'}
              </Button>
            )}
          </CopyButton>
        </Group>
      </Box>
      <Alert color="yellow" variant="light" icon={<IconAlertCircle size={16} />}>
        This is the only time it is shown. Give it to them in person; they should change it from the account menu the first time they sign in.
      </Alert>
      <Group justify="flex-end"><Button onClick={onClose}>Done</Button></Group>
    </Stack>
  );
}

function Form({ onDone }) {
  const create = useCreateStaff();
  const [values, setValues] = useState(blank);
  const [errors, setErrors] = useState({});
  const [result, setResult] = useState(null);

  const set = (field) => (event) => {
    const value = event?.currentTarget ? event.currentTarget.value : event;
    setValues((v) => ({ ...v, [field]: value }));
  };

  if (result) return <Created result={result} onClose={onDone} />;

  const submit = (event) => {
    event.preventDefault();
    const found = staffProblems(values);
    setErrors(found);
    if (Object.keys(found).length) return;
    create.mutate(createBody(values), {
      onSuccess: setResult,
      onError: (error) => setErrors(error.fields ?? {}),
    });
  };

  return (
    <form onSubmit={submit}>
      <Stack gap="sm">
        <Select label="Role" data={STAFF_ROLES} allowDeselect={false} value={values.role} onChange={set('role')} />
        <TextInput label="Full name" placeholder="Dr. Asha Mehra" maxLength={200} data-autofocus value={values.fullName} onChange={set('fullName')} error={errors.fullName} />
        <TextInput label="Email" placeholder="asha.mehra@clinic.example" type="email" maxLength={254} value={values.email} onChange={set('email')} error={errors.email} />
        {values.role === 'DOCTOR' && (
          <TextInput label="Specialization" placeholder="General Medicine" maxLength={120} value={values.specialization} onChange={set('specialization')} error={errors.specialization} />
        )}
        {values.role === 'PATHOLOGIST' && (
          <>
            <TextInput label="Qualification" description="Printed on every report they verify" placeholder="MD Pathology" maxLength={120} value={values.qualification} onChange={set('qualification')} error={errors.qualification} />
            <TextInput label="Registration number" placeholder="MMC-2016-04821" maxLength={60} className="mono" value={values.registrationNumber} onChange={set('registrationNumber')} error={errors.registrationNumber} />
          </>
        )}
        <Text size="xs" c="var(--text-muted)">A temporary password is made for them and shown once, after you add the account.</Text>
        {create.isError && !create.error.fields && (
          <Alert color="red" variant="light" icon={<IconAlertCircle size={16} />}>{friendlyMessage(create.error)}</Alert>
        )}
        <Group justify="flex-end" mt="xs">
          <Button variant="default" onClick={onDone}>Cancel</Button>
          <Button type="submit" loading={create.isPending}>Add account</Button>
        </Group>
      </Stack>
    </form>
  );
}

/** Admin: add a doctor, pathologist, front desk, lab or admin account. */
export function StaffEditor({ opened, onClose }) {
  return (
    <Modal opened={opened} onClose={onClose} title="Add a staff account" radius="lg" centered>
      {opened && <Form onDone={onClose} />}
    </Modal>
  );
}
