'use client';

import { Badge, Box, Button, Grid, Group, ScrollArea, Stack, Table, Text } from '@mantine/core';
import { IconCalendarDue, IconDownload, IconExternalLink, IconFileText, IconLock } from '@tabler/icons-react';
import { formatDate, formatDateTime } from '@/lib/format';
import { prescriptionPdfUrl } from '@/lib/consultations';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { Panel } from '@/components/ui/Panel';
import { Reveal } from '@/components/ui/Reveal';
import { SafetyBanner } from '@/components/ui/SafetyBanner';
import { vitalsSummary } from './VitalsInput';

function Section({ label, children }) {
  if (!children) return null;
  return (
    <div>
      <Text size="xs" fw={700} tt="uppercase" c="var(--text-subtle)" style={{ letterSpacing: '0.06em' }} mb={4}>
        {label}
      </Text>
      <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{children}</Text>
    </div>
  );
}

export function PrescriptionActions({ prescription }) {
  return (
    <Group gap="sm">
      <Button component="a" href={prescriptionPdfUrl(prescription.id)} target="_blank" rel="noopener" variant="default" leftSection={<IconExternalLink size={16} />}>
        View PDF
      </Button>
      <Button component="a" href={prescriptionPdfUrl(prescription.id, true)} leftSection={<IconDownload size={16} />}>
        Download
      </Button>
    </Group>
  );
}

/** Medicines as a clean table (read-only). */
export function MedicineTable({ medicines }) {
  return (
    <ScrollArea>
      <Table verticalSpacing={10} miw={560}>
        <Table.Thead>
          <Table.Tr>
            {['#', 'Medicine', 'Dose', 'Frequency', 'Duration', 'Instructions'].map((h) => (
              <Table.Th key={h} fz="xs" c="var(--text-subtle)" tt="uppercase" style={{ letterSpacing: '0.05em' }}>{h}</Table.Th>
            ))}
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {medicines.map((m, i) => (
            <Table.Tr key={i}>
              <Table.Td><Text size="sm" c="var(--text-subtle)" className="mono">{i + 1}</Text></Table.Td>
              <Table.Td><Text size="sm" fw={600}>{m.medicine}</Text></Table.Td>
              <Table.Td><Text size="sm">{m.dosage ?? '—'}</Text></Table.Td>
              <Table.Td><Text size="sm" className="mono">{m.frequency}</Text></Table.Td>
              <Table.Td><Text size="sm">{m.duration}</Text></Table.Td>
              <Table.Td><Text size="sm" c="var(--text-muted)">{m.instructions ?? ''}</Text></Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </ScrollArea>
  );
}

/**
 * A finished consultation, read-only. Patients get it without the doctor's working notes (the API
 * leaves them out). Shows the issued prescription with PDF buttons.
 */
export function ConsultationSummary({ consultation: c, audience = 'doctor' }) {
  const vitals = vitalsSummary(c.vitals);
  const rx = c.prescription;

  return (
    <Stack gap="lg">
      <Reveal y={10}>
        <GlowCard p="xl">
          <Group justify="space-between" align="flex-start" wrap="wrap" gap="md">
            <div>
              <Group gap={8} mb={6}>
                <Badge radius="sm" color="dark" variant="light" leftSection={<IconLock size={12} />} styles={{ root: { textTransform: 'none' } }}>
                  Completed {formatDateTime(c.completedAt)}
                </Badge>
              </Group>
              <Text fz={24} fw={600} lh={1.2} style={{ letterSpacing: '-0.02em' }}>{c.diagnosis}</Text>
              <Text size="sm" c="var(--text-muted)" mt={4}>
                {audience === 'doctor' ? `${c.patient.fullName} · ${c.patient.patientCode}` : `${c.doctor.fullName} · ${c.doctor.specialization}`}
                {' · '}{formatDate(c.visitAt)}
              </Text>
            </div>
            {rx && <PrescriptionActions prescription={rx} />}
          </Group>
          {c.patient.knownAllergies && (
            <Box mt="md"><SafetyBanner title="Allergies:">{c.patient.knownAllergies}</SafetyBanner></Box>
          )}
        </GlowCard>
      </Reveal>

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: 5 }}>
          <Reveal delay={0.05}>
            <Panel title="Visit">
              <Stack gap="md">
                <Section label="Complaint">{c.chiefComplaint}</Section>
                <Section label="Vitals">{vitals}</Section>
                {audience === 'doctor' && <Section label="Clinical notes">{c.notes}</Section>}
                <Section label="Advice">{c.advice}</Section>
                {c.followUpDate && (
                  <Group gap={8}>
                    <IconCalendarDue size={16} color="var(--accent)" />
                    <Text size="sm" fw={600}>Follow-up on {formatDate(c.followUpDate)}</Text>
                  </Group>
                )}
              </Stack>
            </Panel>
          </Reveal>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 7 }}>
          <Reveal delay={0.1}>
            <Panel
              title={rx ? `Prescription ${rx.prescriptionCode}` : 'Prescription'}
              subtitle={rx ? `Issued ${formatDateTime(rx.issuedAt)} · ${c.doctor.fullName}` : null}
            >
              {rx ? (
                <MedicineTable medicines={rx.medicines} />
              ) : (
                <EmptyState icon={IconFileText} title="No medicines prescribed" compact>
                  This visit ended without a prescription.
                </EmptyState>
              )}
            </Panel>
          </Reveal>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
