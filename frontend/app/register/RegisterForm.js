'use client';

import {
  Alert,
  Anchor,
  Button,
  Group,
  PasswordInput,
  Progress,
  SegmentedControl,
  Stack,
  Stepper,
  Text,
  TextInput,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertCircle, IconArrowLeft, IconArrowRight, IconCheck } from '@tabler/icons-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { friendlyMessage } from '@/lib/errors';
import { useRegister } from '@/lib/auth';
import { ROLE_HOME } from '@/lib/roleRoutes';

const STEP_FIELDS = [
  ['email', 'password'],
  ['fullName', 'dob', 'gender', 'phone'],
];

/** 0–100 strength estimate, just to guide the user; the server enforces the real rule (8–72 chars). */
function passwordStrength(password) {
  let score = 0;
  if (password.length >= 8) score += 30;
  if (password.length >= 12) score += 20;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 20;
  if (/\d/.test(password)) score += 15;
  if (/[^A-Za-z0-9]/.test(password)) score += 15;
  return Math.min(score, 100);
}

export function RegisterForm() {
  const router = useRouter();
  const register = useRegister();
  const [step, setStep] = useState(0);
  const today = new Date().toISOString().slice(0, 10);

  const form = useForm({
    initialValues: { email: '', password: '', fullName: '', dob: '', gender: 'FEMALE', phone: '' },
    validate: {
      email: (v) => (/^\S+@\S+\.\S+$/.test(v.trim()) ? null : 'Enter a valid email address'),
      password: (v) => (v.length >= 8 && v.length <= 72 ? null : 'Use 8 to 72 characters'),
      fullName: (v) => (v.trim() ? null : 'Enter your full name'),
      dob: (v) => (v && v < today ? null : 'Enter your date of birth'),
      phone: (v) => (/^\+?[0-9 ()-]{7,20}$/.test(v.trim()) ? null : 'Enter a valid phone number'),
    },
  });

  const next = () => {
    const invalid = STEP_FIELDS[step].some((field) => form.validateField(field).hasError);
    if (!invalid) setStep(1);
  };

  const submit = form.onSubmit((values) => {
    register.mutate(
      { ...values, email: values.email.trim(), fullName: values.fullName.trim(), phone: values.phone.trim() },
      {
        onSuccess: () => router.replace(ROLE_HOME.PATIENT),
        onError: (error) => {
          if (error.fields) {
            form.setErrors(error.fields);
            if (Object.keys(error.fields).some((f) => STEP_FIELDS[0].includes(f))) setStep(0);
          }
          if (error.code === 'EMAIL_TAKEN') setStep(0);
        },
      },
    );
  });

  const strength = passwordStrength(form.values.password);
  const strengthColor = strength < 50 ? 'red' : strength < 80 ? 'yellow' : 'teal';

  return (
    <form onSubmit={submit} noValidate>
      <Stack gap="lg">
        <Stepper active={step} size="sm" allowNextStepsSelect={false} onStepClick={setStep}>
          <Stepper.Step label="Account" description="Email & password" />
          <Stepper.Step label="About you" description="For your records" />
        </Stepper>

        {register.error && (
          <Alert color="red" variant="light" icon={<IconAlertCircle size={18} />} radius="md">
            {friendlyMessage(register.error)}
          </Alert>
        )}

        {step === 0 ? (
          <Stack gap="md">
            <TextInput label="Email" type="email" autoComplete="email" size="md" autoFocus {...form.getInputProps('email')} />
            <div>
              <PasswordInput label="Password" autoComplete="new-password" size="md" {...form.getInputProps('password')} />
              {form.values.password && (
                <Progress value={strength} color={strengthColor} size={4} mt={8} radius="xl" aria-label="Password strength" />
              )}
            </div>
            <Button size="md" onClick={next} rightSection={<IconArrowRight size={18} />}>
              Continue
            </Button>
          </Stack>
        ) : (
          <Stack gap="md">
            <TextInput label="Full name" autoComplete="name" size="md" autoFocus {...form.getInputProps('fullName')} />
            <Group grow align="flex-start">
              <TextInput label="Date of birth" type="date" max={today} size="md" {...form.getInputProps('dob')} />
              <TextInput label="Phone" type="tel" autoComplete="tel" placeholder="+91 98765 43210" size="md" {...form.getInputProps('phone')} />
            </Group>
            <div>
              <Text size="sm" fw={500} mb={6}>
                Gender
              </Text>
              <SegmentedControl
                fullWidth
                data={[
                  { label: 'Female', value: 'FEMALE' },
                  { label: 'Male', value: 'MALE' },
                  { label: 'Other', value: 'OTHER' },
                ]}
                {...form.getInputProps('gender')}
              />
            </div>
            <Group grow>
              <Button variant="default" size="md" onClick={() => setStep(0)} leftSection={<IconArrowLeft size={18} />}>
                Back
              </Button>
              <Button type="submit" size="md" loading={register.isPending} rightSection={<IconCheck size={18} />}>
                Create account
              </Button>
            </Group>
          </Stack>
        )}

        <Text size="sm" c="var(--text-muted)" ta="center">
          Already have an account?{' '}
          <Anchor component={Link} href="/login" fw={600}>
            Sign in
          </Anchor>
        </Text>
      </Stack>
    </form>
  );
}
