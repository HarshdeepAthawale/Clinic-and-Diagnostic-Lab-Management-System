'use client';

import { SegmentedControl, Stack } from '@mantine/core';
import { useState } from 'react';
import { ClaimForm } from './ClaimForm';
import { RegisterForm } from './RegisterForm';

/** New patients sign up directly; patients registered at the desk link their record with a code. */
export function RegisterChooser() {
  const [mode, setMode] = useState('new');
  return (
    <Stack gap="lg">
      <SegmentedControl
        fullWidth
        radius="md"
        value={mode}
        onChange={setMode}
        data={[
          { label: 'New patient', value: 'new' },
          { label: 'I have a registration code', value: 'claim' },
        ]}
      />
      {mode === 'new' ? <RegisterForm /> : <ClaimForm />}
    </Stack>
  );
}
