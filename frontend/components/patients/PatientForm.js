'use client';

import { Alert, Button, Divider, Grid, Group, SegmentedControl, Select, Stack, Text, TextInput, Textarea } from '@mantine/core';
import { useForm } from '@mantine/form';
import { friendlyMessage } from '@/lib/errors';
import { BLOOD_GROUPS } from './ClinicalEditModal';

const PHONE = /^\+?[0-9 ()-]{7,20}$/;

function SectionLabel({ children }) {
  return (
    <Text size="xs" fw={700} tt="uppercase" c="var(--text-subtle)" style={{ letterSpacing: '0.06em' }}>
      {children}
    </Text>
  );
}

/**
 * Patient details form for the front desk. `mode="register"` also captures intake clinical details
 * (allergies, blood group, history); `mode="edit"` is demographics only — clinical fields are the
 * doctor's to change.
 */
export function PatientForm({ mode = 'register', initialValues, onSubmit, submitting, error, submitLabel }) {
  const today = new Date().toISOString().slice(0, 10);
  const form = useForm({
    initialValues: {
      fullName: '',
      dob: '',
      gender: 'FEMALE',
      phone: '',
      address: '',
      emergencyContactName: '',
      emergencyContactPhone: '',
      knownAllergies: '',
      bloodGroup: null,
      medicalHistory: '',
      ...initialValues,
    },
    validate: {
      fullName: (v) => (v.trim() ? null : 'Enter the full name'),
      dob: (v) => (v && v < today ? null : 'Enter a past date of birth'),
      phone: (v) => (PHONE.test(v.trim()) ? null : 'Enter a valid phone number'),
      emergencyContactPhone: (v) => (!v || PHONE.test(v.trim()) ? null : 'Enter a valid phone number'),
    },
  });

  const submit = form.onSubmit((values) => {
    const body = { ...values };
    if (mode === 'edit') {
      delete body.knownAllergies;
      delete body.bloodGroup;
      delete body.medicalHistory;
    }
    onSubmit(body, { setErrors: form.setErrors });
  });

  return (
    <form onSubmit={submit} noValidate>
      <Stack gap="lg">
        {error && !error.fields && (
          <Alert color="red" variant="light" radius="md">{friendlyMessage(error)}</Alert>
        )}

        <SectionLabel>Identity</SectionLabel>
        <Grid gutter="md">
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <TextInput label="Full name" autoComplete="off" data-autofocus autoFocus {...form.getInputProps('fullName')} />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <TextInput label="Date of birth" type="date" max={today} {...form.getInputProps('dob')} />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <Text size="sm" fw={500} mb={4}>Gender</Text>
            <SegmentedControl
              fullWidth
              size="sm"
              data={[
                { label: 'F', value: 'FEMALE' },
                { label: 'M', value: 'MALE' },
                { label: 'Other', value: 'OTHER' },
              ]}
              {...form.getInputProps('gender')}
            />
          </Grid.Col>
        </Grid>

        <SectionLabel>Contact</SectionLabel>
        <Grid gutter="md">
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <TextInput label="Phone" type="tel" placeholder="+91 98765 43210" {...form.getInputProps('phone')} />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 8 }}>
            <TextInput label="Address" {...form.getInputProps('address')} />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <TextInput label="Emergency contact name" {...form.getInputProps('emergencyContactName')} />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <TextInput label="Emergency contact phone" type="tel" {...form.getInputProps('emergencyContactPhone')} />
          </Grid.Col>
        </Grid>

        {mode === 'register' && (
          <>
            <Divider />
            <SectionLabel>Clinical intake · optional</SectionLabel>
            <Grid gutter="md">
              <Grid.Col span={{ base: 12, sm: 8 }}>
                <TextInput
                  label="Known allergies"
                  description="Shown to every clinician in a red safety banner"
                  placeholder="e.g. Penicillin (rash)"
                  {...form.getInputProps('knownAllergies')}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 12, sm: 4 }}>
                <Select label="Blood group" description="If known" placeholder="Not known" data={BLOOD_GROUPS} clearable {...form.getInputProps('bloodGroup')} />
              </Grid.Col>
              <Grid.Col span={12}>
                <Textarea label="Medical history" description="Chronic conditions, past surgeries, current medication" autosize minRows={2} {...form.getInputProps('medicalHistory')} />
              </Grid.Col>
            </Grid>
          </>
        )}

        <Group justify="flex-end">
          <Button type="submit" size="md" loading={submitting}>
            {submitLabel}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
