'use client';

import { Alert, Button, Group, Modal, Select, Stack, Text, Textarea } from '@mantine/core';
import { useForm } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import { friendlyMessage } from '@/lib/errors';
import { useUpdateClinical } from '@/lib/patients';

export const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

/** Doctor-only: update allergies, medical history and blood group (care relationship required). */
export function ClinicalEditModal({ record, opened, onClose }) {
  const update = useUpdateClinical(record.id);
  const form = useForm({
    initialValues: {
      knownAllergies: record.knownAllergies ?? '',
      medicalHistory: record.medicalHistory ?? '',
      bloodGroup: record.bloodGroup ?? null,
    },
  });

  const submit = form.onSubmit((values) =>
    update.mutate(values, {
      onSuccess: () => {
        notifications.show({ title: 'Record updated', message: `${record.fullName}'s clinical details were saved.`, color: 'teal', radius: 'lg' });
        onClose();
      },
      onError: (error) => error.fields && form.setErrors(error.fields),
    }),
  );

  return (
    <Modal opened={opened} onClose={onClose} title={<Text fw={600}>Update clinical details</Text>} size="lg">
      <form onSubmit={submit}>
        <Stack gap="md">
          {update.error && !update.error.fields && (
            <Alert color="red" variant="light" radius="md">{friendlyMessage(update.error)}</Alert>
          )}
          <Textarea
            label="Known allergies"
            description="Shown to every clinician in a red safety banner. Include the reaction if known."
            placeholder="e.g. Penicillin (rash)"
            autosize
            minRows={2}
            {...form.getInputProps('knownAllergies')}
          />
          <Textarea
            label="Medical history"
            description="Chronic conditions, past surgeries, current medication."
            autosize
            minRows={4}
            {...form.getInputProps('medicalHistory')}
          />
          <Select label="Blood group" placeholder="Not recorded" data={BLOOD_GROUPS} clearable {...form.getInputProps('bloodGroup')} />
          <Group justify="flex-end" gap="sm" mt="xs">
            <Button variant="default" onClick={onClose}>Cancel</Button>
            <Button type="submit" loading={update.isPending}>Save changes</Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
