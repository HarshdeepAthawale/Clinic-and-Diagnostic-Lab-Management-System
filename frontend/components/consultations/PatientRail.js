'use client';

import { Avatar, Box, Group, Stack, Text } from '@mantine/core';
import { IconDropletFilled, IconShieldCheck } from '@tabler/icons-react';
import { ageGender } from '@/lib/format';
import { usePatientRecord } from '@/lib/patients';
import { Panel } from '@/components/ui/Panel';
import { SafetyBanner } from '@/components/ui/SafetyBanner';
import { initials } from '@/components/shell/UserMenu';
import { VisitHistory } from '@/components/patients/VisitHistory';

/**
 * Left rail of the consult workspace (Design.md §5.5): who the patient is, allergies pinned in a
 * red safety banner, blood group, history, and previous visits.
 */
export function PatientRail({ patient, consultationId }) {
  const record = usePatientRecord(patient.id);
  const history = record.data?.medicalHistory;

  return (
    <Stack gap="lg" style={{ position: 'sticky', top: 96 }}>
      <Panel title="Patient">
        <Group gap="md" wrap="nowrap" mb="md">
          <Avatar size={48} radius="xl" styles={{ placeholder: { background: 'var(--ink)', color: 'var(--on-ink)', fontWeight: 600 } }}>
            {initials(patient.fullName)}
          </Avatar>
          <div>
            <Text fw={600} size="lg" lh={1.2}>{patient.fullName}</Text>
            <Text size="sm" c="var(--text-muted)" className="mono">
              {patient.patientCode} · {ageGender(patient.age, patient.gender)}
            </Text>
          </div>
        </Group>

        {patient.knownAllergies ? (
          <SafetyBanner title="Allergies:">{patient.knownAllergies}</SafetyBanner>
        ) : (
          <Group gap={8} px="md" py={8} style={{ borderRadius: 'var(--radius-sm)', background: 'var(--success-soft)' }}>
            <IconShieldCheck size={16} color="var(--success)" />
            <Text size="sm" c="var(--success)" fw={600}>No known allergies</Text>
          </Group>
        )}

        <Group gap={6} mt="md">
          <IconDropletFilled size={14} color={patient.bloodGroup ? 'var(--accent)' : 'var(--text-subtle)'} />
          <Text size="sm">{patient.bloodGroup ? `Blood group ${patient.bloodGroup}` : 'Blood group not recorded'}</Text>
        </Group>

        <Box mt="md">
          <Text size="xs" fw={700} tt="uppercase" c="var(--text-subtle)" style={{ letterSpacing: '0.06em' }} mb={4}>
            Medical history
          </Text>
          <Text size="sm" c={history ? undefined : 'var(--text-subtle)'} style={{ whiteSpace: 'pre-wrap' }} lineClamp={6}>
            {history || 'Nothing recorded.'}
          </Text>
        </Box>
      </Panel>

      <Panel title="Previous visits">
        <VisitHistory
          patientId={patient.id}
          excludeId={consultationId}
          limit={5}
          compact
          hrefFor={(v) => `/doctor/consultations/${v.id}`}
        />
      </Panel>
    </Stack>
  );
}
