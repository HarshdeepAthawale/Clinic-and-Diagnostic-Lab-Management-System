'use client';

import { Avatar, Box, Grid, Group, SimpleGrid, Stack, Text } from '@mantine/core';
import { IconDropletFilled, IconFileText, IconShieldCheck } from '@tabler/icons-react';
import { ageGender, formatDate, formatRelative } from '@/lib/format';
import { GlowCard } from '@/components/ui/GlowCard';
import { Panel } from '@/components/ui/Panel';
import { Reveal } from '@/components/ui/Reveal';
import { SafetyBanner } from '@/components/ui/SafetyBanner';
import { initials } from '@/components/shell/UserMenu';
import { VisitHistory } from './VisitHistory';

function Field({ label, children, mono = false }) {
  return (
    <div>
      <Text size="xs" c="var(--text-muted)" mb={2}>
        {label}
      </Text>
      <Text size="sm" fw={500} className={mono ? 'mono' : undefined} style={{ whiteSpace: 'pre-wrap' }}>
        {children || <Text span c="var(--text-subtle)">Not recorded</Text>}
      </Text>
    </div>
  );
}

/**
 * The full medical record (EMR) — for the patient themself and doctors with a care relationship.
 * Allergies are always pinned at the top in a safety banner (Design.md §4).
 */
export function PatientRecordView({ record, actions, audience = 'staff' }) {
  return (
    <Stack gap="lg">
      <Reveal y={10}>
        <GlowCard p="xl">
          <Group justify="space-between" align="flex-start" wrap="wrap" gap="md">
            <Group gap="lg" wrap="nowrap">
              <Avatar size={64} radius="xl" styles={{ placeholder: { background: 'var(--ink)', color: 'var(--on-ink)', fontSize: 22, fontWeight: 600 } }}>
                {initials(record.fullName)}
              </Avatar>
              <div>
                <Text fz={26} fw={600} lh={1.15} style={{ letterSpacing: '-0.02em' }}>
                  {record.fullName}
                </Text>
                <Text c="var(--text-muted)" className="mono" size="sm" mt={4}>
                  {record.patientCode} · {ageGender(record.age, record.gender)} · born {formatDate(record.dob)}
                </Text>
                <Group gap={6} mt={8}>
                  <IconDropletFilled size={15} color={record.bloodGroup ? 'var(--accent)' : 'var(--text-subtle)'} />
                  <Text size="sm" fw={600}>
                    {record.bloodGroup ? `Blood group ${record.bloodGroup}` : 'Blood group not recorded'}
                  </Text>
                </Group>
              </div>
            </Group>
            {actions}
          </Group>

          <Box mt="lg">
            {record.knownAllergies ? (
              <SafetyBanner title="Allergies:">{record.knownAllergies}</SafetyBanner>
            ) : (
              <Group gap={8} px="md" py={10} style={{ borderRadius: 'var(--radius-sm)', background: 'var(--success-soft)' }}>
                <IconShieldCheck size={17} color="var(--success)" />
                <Text size="sm" c="var(--success)" fw={600}>
                  No known allergies recorded
                </Text>
              </Group>
            )}
          </Box>
        </GlowCard>
      </Reveal>

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: 7 }}>
          <Reveal delay={0.05}>
            <Panel title="Medical history" subtitle={`Updated ${formatRelative(record.updatedAt)}`}>
              <Text size="sm" style={{ whiteSpace: 'pre-wrap' }} c={record.medicalHistory ? undefined : 'var(--text-subtle)'}>
                {record.medicalHistory ||
                  (audience === 'patient'
                    ? 'Nothing recorded yet. Your doctor adds your history during visits.'
                    : 'Nothing recorded yet. Add chronic conditions, past surgeries and current medication.')}
              </Text>
            </Panel>
          </Reveal>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 5 }}>
          <Reveal delay={0.1}>
            <Panel title="Contact">
              <SimpleGrid cols={1} spacing="md">
                <Field label="Phone" mono>{record.phone}</Field>
                <Field label="Address">{record.address}</Field>
                <Field label="Emergency contact">
                  {record.emergencyContactName &&
                    `${record.emergencyContactName}${record.emergencyContactPhone ? ` · ${record.emergencyContactPhone}` : ''}`}
                </Field>
              </SimpleGrid>
            </Panel>
          </Reveal>
        </Grid.Col>
      </Grid>

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: 8 }}>
          <Reveal delay={0.1}>
            <Panel title="Visits & prescriptions" subtitle="Completed consultations, newest first">
              <VisitHistory
                patientId={record.id}
                hrefFor={(v) =>
                  audience === 'patient'
                    ? v.prescriptionId ? `/patient/prescriptions/${v.prescriptionId}` : null
                    : `/doctor/consultations/${v.id}`
                }
              />
            </Panel>
          </Reveal>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 4 }}>
          <Reveal delay={0.12}>
            <Box p="md" style={{ borderRadius: 'var(--radius-lg)', border: '1px dashed var(--border-strong)' }}>
              <Group gap="sm" mb={4}>
                <IconFileText size={18} color="var(--text-muted)" stroke={1.6} />
                <Text fw={600} size="sm">Lab reports</Text>
              </Group>
              <Text size="xs" c="var(--text-subtle)">Appears in this record from Phase 08.</Text>
            </Box>
          </Reveal>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
