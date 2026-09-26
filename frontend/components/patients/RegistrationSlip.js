'use client';

import { Box, Button, CopyButton, Group, Stack, Text, Tooltip } from '@mantine/core';
import { IconCheck, IconCopy, IconPrinter } from '@tabler/icons-react';
import { ageGender, formatDate } from '@/lib/format';
import classes from './RegistrationSlip.module.css';

/**
 * Printable slip handed to the patient after front-desk registration: patient ID plus the one-time
 * registration code that links their own login to this record (ADR-018). The code is only ever
 * shown here — the server keeps just a hash.
 */
export function RegistrationSlip({ patient, code, expiresAt }) {
  return (
    <Stack gap="md">
      <Box className={classes.slip} id="registration-slip">
        <Group justify="space-between" align="flex-start" mb="lg">
          <div>
            <Text fw={700} size="lg" style={{ letterSpacing: '-0.03em' }}>CDLMS</Text>
            <Text size="xs" c="var(--text-muted)">Clinic & Diagnostic Lab · Patient registration</Text>
          </div>
          <Text size="xs" c="var(--text-muted)">{formatDate(patient.createdAt)}</Text>
        </Group>

        <Text size="xs" c="var(--text-muted)">Patient</Text>
        <Text fw={600} size="xl">{patient.fullName}</Text>
        <Text size="sm" c="var(--text-muted)" className="mono" mb="lg">
          {patient.patientCode} · {ageGender(patient.age, patient.gender)}
        </Text>

        <Box className={classes.codeBox}>
          <Text size="xs" fw={600} tt="uppercase" c="var(--text-muted)" style={{ letterSpacing: '0.06em' }}>
            Your registration code
          </Text>
          <Text className={`mono ${classes.code}`}>{code}</Text>
          <Text size="xs" c="var(--text-muted)">Valid until {formatDate(expiresAt)} · can be used once</Text>
        </Box>

        <Text size="sm" mt="lg">
          To see your records online, go to the patient portal, choose <b>Create account → I have a registration code</b>,
          and enter the code above. Keep this slip private.
        </Text>
      </Box>

      <Group className={classes.noPrint} gap="sm">
        <Button leftSection={<IconPrinter size={16} />} onClick={() => window.print()}>
          Print slip
        </Button>
        <CopyButton value={code}>
          {({ copied, copy }) => (
            <Tooltip label={copied ? 'Copied' : 'Copy code'}>
              <Button variant="default" onClick={copy} leftSection={copied ? <IconCheck size={16} /> : <IconCopy size={16} />}>
                {copied ? 'Copied' : 'Copy code'}
              </Button>
            </Tooltip>
          )}
        </CopyButton>
      </Group>
    </Stack>
  );
}
