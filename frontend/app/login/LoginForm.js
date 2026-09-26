'use client';

import { Alert, Anchor, Button, Group, PasswordInput, Stack, Text, TextInput, UnstyledButton } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertCircle, IconArrowRight } from '@tabler/icons-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { friendlyMessage } from '@/lib/errors';
import { useLogin } from '@/lib/auth';
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from '@/lib/demoAccounts';
import { landingPath } from '@/lib/roleRoutes';
import { roleConfig } from '@/lib/roles';

const SHOW_DEMO = process.env.NODE_ENV === 'development';

export function LoginForm() {
  const router = useRouter();
  const next = useSearchParams().get('next');
  const login = useLogin();

  const form = useForm({
    mode: 'uncontrolled',
    initialValues: { email: '', password: '' },
    validate: {
      email: (value) => (/^\S+@\S+\.\S+$/.test(value.trim()) ? null : 'Enter a valid email address'),
      password: (value) => (value ? null : 'Enter your password'),
    },
  });

  const submit = form.onSubmit((values) => {
    login.mutate(
      { email: values.email.trim(), password: values.password },
      { onSuccess: (me) => router.replace(landingPath(me.role, next)) },
    );
  });

  return (
    <form onSubmit={submit} noValidate>
      <Stack gap="md">
        {login.error && (
          <Alert color="red" variant="light" icon={<IconAlertCircle size={18} />} radius="md">
            {friendlyMessage(login.error)}
          </Alert>
        )}

        <TextInput
          label="Email"
          placeholder="you@clinic.com"
          type="email"
          autoComplete="email"
          autoFocus
          size="md"
          key={form.key('email')}
          {...form.getInputProps('email')}
        />
        <PasswordInput
          label="Password"
          placeholder="Your password"
          autoComplete="current-password"
          size="md"
          key={form.key('password')}
          {...form.getInputProps('password')}
        />

        <Button
          type="submit"
          size="md"
          fullWidth
          loading={login.isPending}
          rightSection={<IconArrowRight size={18} />}
          mt={4}
        >
          Sign in
        </Button>

        <Text size="sm" c="var(--text-muted)" ta="center">
          New patient?{' '}
          <Anchor component={Link} href="/register" fw={600}>
            Create an account
          </Anchor>
        </Text>

        {SHOW_DEMO && (
          <Stack gap={8} mt="lg" p="md" style={{ border: '1px dashed var(--border-strong)', borderRadius: 12 }}>
            <Text size="xs" fw={600} c="var(--text-muted)" tt="uppercase" style={{ letterSpacing: '0.06em' }}>
              Demo accounts · dev only
            </Text>
            <Group gap={6}>
              {DEMO_ACCOUNTS.map((account) => {
                const config = roleConfig(account.role);
                return (
                  <UnstyledButton
                    key={account.email}
                    onClick={() => form.setValues({ email: account.email, password: DEMO_PASSWORD })}
                    style={{
                      fontSize: 12,
                      fontWeight: 600,
                      padding: '5px 10px',
                      borderRadius: 999,
                      color: config.accentVar,
                      background: `color-mix(in srgb, ${config.accentVar} 12%, transparent)`,
                    }}
                  >
                    {config.label}
                  </UnstyledButton>
                );
              })}
            </Group>
          </Stack>
        )}
      </Stack>
    </form>
  );
}
