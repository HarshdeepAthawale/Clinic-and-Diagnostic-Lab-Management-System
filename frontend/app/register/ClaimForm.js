'use client';

import { Alert, Button, PasswordInput, Stack, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertCircle, IconLink } from '@tabler/icons-react';
import { useRouter } from 'next/navigation';
import { useClaimAccount } from '@/lib/auth';
import { friendlyMessage } from '@/lib/errors';
import { ROLE_HOME } from '@/lib/roleRoutes';

/** For patients registered at the front desk: create a login linked to that record. */
export function ClaimForm() {
  const router = useRouter();
  const claim = useClaimAccount();
  const form = useForm({
    initialValues: { registrationCode: '', email: '', password: '' },
    validate: {
      registrationCode: (v) => (v.replace(/[^A-Za-z0-9]/g, '').length === 10 ? null : 'Enter the 10-character code from your slip'),
      email: (v) => (/^\S+@\S+\.\S+$/.test(v.trim()) ? null : 'Enter a valid email address'),
      password: (v) => (v.length >= 8 && v.length <= 72 ? null : 'Use 8 to 72 characters'),
    },
  });

  const submit = form.onSubmit((values) =>
    claim.mutate(
      { ...values, email: values.email.trim() },
      {
        onSuccess: () => router.replace(ROLE_HOME.PATIENT),
        onError: (error) => error.fields && form.setErrors(error.fields),
      },
    ),
  );

  return (
    <form onSubmit={submit} noValidate>
      <Stack gap="md">
        <Text size="sm" c="var(--text-muted)">
          Registered at the clinic? Enter the code printed on your registration slip to link your records to a new login.
        </Text>
        {claim.error && !claim.error.fields && (
          <Alert color="red" variant="light" icon={<IconAlertCircle size={18} />} radius="md">
            {friendlyMessage(claim.error)}
          </Alert>
        )}
        <TextInput
          label="Registration code"
          placeholder="ABCDE-FGH23"
          size="md"
          autoFocus
          styles={{ input: { fontFamily: 'var(--font-mono)', letterSpacing: '0.08em', textTransform: 'uppercase' } }}
          {...form.getInputProps('registrationCode')}
        />
        <TextInput label="Email" type="email" autoComplete="email" size="md" {...form.getInputProps('email')} />
        <PasswordInput label="Choose a password" autoComplete="new-password" size="md" {...form.getInputProps('password')} />
        <Button type="submit" size="md" loading={claim.isPending} leftSection={<IconLink size={18} />}>
          Link my record
        </Button>
      </Stack>
    </form>
  );
}
