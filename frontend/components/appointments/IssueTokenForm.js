'use client';

import { Alert, Button, Group, Stack, Text, Textarea } from '@mantine/core';
import { IconAlertCircle, IconTicket } from '@tabler/icons-react';
import { useState } from 'react';
import { useIssueToken } from '@/lib/appointments';
import { friendlyMessage } from '@/lib/errors';
import { DoctorPicker, PatientPicker } from './Pickers';

/** Walk-in: patient + doctor → next token, checked in immediately. */
export function IssueTokenForm({ initialPatient = null, onIssued }) {
  const issue = useIssueToken();
  const [patient, setPatient] = useState(initialPatient);
  const [doctorId, setDoctorId] = useState(null);
  const [reason, setReason] = useState('');

  return (
    <Stack gap="lg">
      <PatientPicker value={patient} onChange={setPatient} autoFocus={!initialPatient} />
      <div>
        <Text size="sm" fw={500} mb={6}>Doctor</Text>
        <DoctorPicker value={doctorId} onChange={setDoctorId} />
      </div>
      <Textarea label="Reason for visit" placeholder="Optional" maxLength={500} autosize minRows={2} value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
      {issue.isError && (
        <Alert color="red" variant="light" radius="md" icon={<IconAlertCircle size={18} />}>
          {friendlyMessage(issue.error)}
        </Alert>
      )}
      <Group justify="flex-end">
        <Button
          size="md"
          leftSection={<IconTicket size={18} />}
          disabled={!patient || !doctorId}
          loading={issue.isPending}
          onClick={() => issue.mutate({ patientId: patient.id, doctorId, reason: reason.trim() || undefined }, { onSuccess: onIssued })}
        >
          Issue token
        </Button>
      </Group>
    </Stack>
  );
}
