'use client';

import { Alert, Box, Button, Grid, Group, Modal, NumberInput, SimpleGrid, Skeleton, Stack, Text, Textarea, TextInput } from '@mantine/core';
import { useDebouncedCallback } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconArrowLeft, IconCheck, IconCircleCheck, IconCloudCheck, IconDropletFilled, IconLoader2, IconShieldCheck } from '@tabler/icons-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import {
  useCompleteConsultation,
  useConsultationForAppointment,
  useIssuePrescription,
  usePatientConsultations,
  useUpdateConsultation,
} from '@/lib/consultations';
import { friendlyMessage } from '@/lib/errors';
import { ageGender, formatDate, formatDateTime, formatTime } from '@/lib/format';
import { usePatientRecord } from '@/lib/patients';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { Panel } from '@/components/ui/Panel';
import { Reveal } from '@/components/ui/Reveal';
import { SafetyBanner } from '@/components/ui/SafetyBanner';
import { PrescriptionBuilder } from './PrescriptionBuilder';
import { PrescriptionCard } from './PrescriptionCard';

const VITALS = [
  ['bpSystolic', 'BP systolic', 'mmHg', 50, 300, 0],
  ['bpDiastolic', 'BP diastolic', 'mmHg', 20, 200, 0],
  ['pulse', 'Pulse', 'bpm', 20, 250, 0],
  ['temperatureC', 'Temperature', '°C', 30, 45, 1],
  ['weightKg', 'Weight', 'kg', 0.5, 400, 1],
  ['spo2', 'SpO₂', '%', 50, 100, 0],
];

const TEXT_FIELDS = ['chiefComplaint', 'notes', 'diagnosis', 'advice', 'followUpDate'];

function toForm(c) {
  const form = Object.fromEntries(TEXT_FIELDS.map((f) => [f, c[f] ?? '']));
  VITALS.forEach(([key]) => { form[key] = c.vitals?.[key] ?? ''; });
  return form;
}

function toBody(form) {
  const blank = (v) => (v === '' || v === null ? undefined : v);
  return {
    chiefComplaint: blank(form.chiefComplaint),
    notes: blank(form.notes),
    diagnosis: blank(form.diagnosis),
    advice: blank(form.advice),
    followUpDate: blank(form.followUpDate),
    vitals: Object.fromEntries(VITALS.map(([key]) => [key, blank(form[key])])),
  };
}

/** Left rail: who is in the room — identity, allergies (always red, always visible), history, past visits. */
function PatientRail({ consultation }) {
  const record = usePatientRecord(consultation.patient.id);
  const visits = usePatientConsultations(consultation.patient.id);
  const r = record.data;
  const past = (visits.data ?? []).filter((v) => v.id !== consultation.id);

  return (
    <Stack gap="md" style={{ position: 'sticky', top: 96 }}>
      <GlowCard p="lg">
        <Text fz={22} fw={600} lh={1.2} style={{ letterSpacing: '-0.02em' }}>{consultation.patient.fullName}</Text>
        <Text size="sm" c="var(--text-muted)" className="mono" mt={4}>
          {consultation.patient.patientCode} · {ageGender(consultation.patient.age, consultation.patient.gender)}
        </Text>
        {r ? (
          <Stack gap="sm" mt="md">
            <Group gap={6}>
              <IconDropletFilled size={15} color={r.bloodGroup ? 'var(--accent)' : 'var(--text-subtle)'} />
              <Text size="sm" fw={600}>{r.bloodGroup ? `Blood group ${r.bloodGroup}` : 'Blood group not recorded'}</Text>
            </Group>
            {r.knownAllergies ? (
              <SafetyBanner title="Allergies:">{r.knownAllergies}</SafetyBanner>
            ) : (
              <Group gap={8} px="md" py={8} style={{ borderRadius: 'var(--radius-sm)', background: 'var(--success-soft)' }}>
                <IconShieldCheck size={16} color="var(--success)" />
                <Text size="sm" c="var(--success)" fw={600}>No known allergies</Text>
              </Group>
            )}
            <div>
              <Text size="xs" c="var(--text-muted)" mb={2}>Medical history</Text>
              <Text size="sm" style={{ whiteSpace: 'pre-wrap' }} lineClamp={6} c={r.medicalHistory ? undefined : 'var(--text-subtle)'}>
                {r.medicalHistory || 'Nothing recorded'}
              </Text>
            </div>
          </Stack>
        ) : (
          <Skeleton height={120} mt="md" radius="md" />
        )}
      </GlowCard>

      <Panel title="Past visits" subtitle={visits.data ? `${past.length} earlier visit${past.length === 1 ? '' : 's'}` : undefined}>
        {!visits.data ? (
          <Skeleton height={80} radius="md" />
        ) : past.length === 0 ? (
          <Text size="sm" c="var(--text-subtle)">First visit at this clinic.</Text>
        ) : (
          <Stack gap={0}>
            {past.slice(0, 6).map((v) => (
              <Box key={v.id} py={8} style={{ borderTop: '1px solid var(--border)' }}>
                <Group justify="space-between" wrap="nowrap">
                  <Text size="sm" fw={600} truncate>{v.diagnosis || v.chiefComplaint || 'No diagnosis recorded'}</Text>
                  {v.prescription && <Text size="xs" className="mono" c="var(--text-muted)">{v.prescription.code}</Text>}
                </Group>
                <Text size="xs" c="var(--text-muted)">{formatDate(v.date)} · {v.doctor.fullName}</Text>
              </Box>
            ))}
          </Stack>
        )}
      </Panel>
    </Stack>
  );
}

function SaveState({ update, savedAt }) {
  if (update.isPending) return <Group gap={6}><IconLoader2 size={15} className="spin" /><Text size="sm" c="var(--text-muted)">Saving…</Text></Group>;
  if (update.isError) return <Group gap={6}><IconAlertCircle size={15} color="var(--critical)" /><Text size="sm" c="var(--critical)">{friendlyMessage(update.error)}</Text></Group>;
  if (savedAt) return <Group gap={6}><IconCloudCheck size={15} color="var(--success)" /><Text size="sm" c="var(--text-muted)">Saved {formatTime(savedAt)}</Text></Group>;
  return <Text size="sm" c="var(--text-subtle)">Changes save automatically</Text>;
}

function Editor({ consultation, appointmentId }) {
  const router = useRouter();
  const update = useUpdateConsultation(consultation.id, appointmentId);
  const complete = useCompleteConsultation(consultation.id, appointmentId);
  const issue = useIssuePrescription(consultation.id, appointmentId);
  const [form, setForm] = useState(() => toForm(consultation));
  const [savedAt, setSavedAt] = useState(null);
  const [revising, setRevising] = useState(null);
  const latest = useRef(form);
  useEffect(() => {
    latest.current = form;
  }, [form]);

  const save = () => update.mutateAsync(toBody(latest.current)).then(() => setSavedAt(new Date().toISOString()));
  const scheduleSave = useDebouncedCallback(() => save().catch(() => {}), 800);
  const set = (key, value) => {
    setForm((f) => ({ ...f, [key]: value }));
    scheduleSave();
  };

  // Don't lose the last keystrokes if the doctor navigates away mid-debounce.
  useEffect(() => () => scheduleSave.flush?.(), [scheduleSave]);

  const finish = async () => {
    try {
      await save();
      await complete.mutateAsync();
      notifications.show({ message: `Consultation with ${consultation.patient.fullName} finished.`, color: 'teal', radius: 'lg' });
      router.push('/doctor');
    } catch (error) {
      notifications.show({ title: "Couldn't finish", message: friendlyMessage(error), color: 'red', radius: 'lg' });
    }
  };

  const current = consultation.prescriptions.filter((rx) => !rx.supersededBy);

  return (
    <Stack gap="lg" pb={96}>
      <Panel title="Consultation" subtitle={`Started ${formatTime(consultation.createdAt)}`}>
        <Stack gap="md">
          <TextInput label="Chief complaint" placeholder="What brings the patient in" value={form.chiefComplaint} maxLength={500} onChange={(e) => set('chiefComplaint', e.currentTarget.value)} />
          <div>
            <Text size="sm" fw={500} mb={6}>Vitals</Text>
            <SimpleGrid cols={{ base: 2, sm: 3 }} spacing="sm">
              {VITALS.map(([key, label, unit, min, max, scale]) => (
                <NumberInput
                  key={key}
                  label={<Text span size="xs" c="var(--text-muted)">{label}</Text>}
                  rightSection={<Text size="xs" c="var(--text-subtle)" pr={6}>{unit}</Text>}
                  rightSectionWidth={44}
                  min={min}
                  max={max}
                  decimalScale={scale}
                  hideControls
                  value={form[key]}
                  onChange={(v) => set(key, v)}
                  styles={{ input: { fontFamily: 'var(--font-mono), monospace' } }}
                />
              ))}
            </SimpleGrid>
          </div>
          <Textarea label="Clinical notes" description="Visible to doctors treating this patient, not to the patient." placeholder="History, examination findings…" autosize minRows={4} maxLength={20000} value={form.notes} onChange={(e) => set('notes', e.currentTarget.value)} />
          <TextInput label="Diagnosis" placeholder="Required to finish" value={form.diagnosis} maxLength={500} onChange={(e) => set('diagnosis', e.currentTarget.value)} styles={{ input: { fontWeight: 600 } }} />
          <Grid gutter="md">
            <Grid.Col span={{ base: 12, sm: 8 }}>
              <Textarea label="Advice for the patient" placeholder="Shown to the patient and printed on the prescription" autosize minRows={2} maxLength={4000} value={form.advice} onChange={(e) => set('advice', e.currentTarget.value)} />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 4 }}>
              <TextInput type="date" label="Follow-up" value={form.followUpDate} onChange={(e) => set('followUpDate', e.currentTarget.value)} />
            </Grid.Col>
          </Grid>
        </Stack>
      </Panel>

      <Panel title="Prescription" subtitle={current.length ? 'Issued — revise to correct it' : 'Add medicines, then issue'}>
        {current.length === 0 ? (
          <PrescriptionBuilder mutation={issue} initialAdvice={form.advice} onDone={() => notifications.show({ message: 'Prescription issued. The patient can see it now.', color: 'teal', radius: 'lg' })} />
        ) : (
          <Stack gap="md">
            {consultation.prescriptions.slice().reverse().map((rx) => (
              <PrescriptionCard key={rx.id} rx={rx} onRevise={setRevising} />
            ))}
          </Stack>
        )}
      </Panel>

      <Modal opened={Boolean(revising)} onClose={() => setRevising(null)} title={<Text fw={600}>Revise {revising?.code}</Text>} size="xl" radius="lg">
        {revising && (
          <PrescriptionBuilder
            mutation={issue}
            reviseOf={revising}
            onCancel={() => setRevising(null)}
            onDone={() => { setRevising(null); notifications.show({ message: 'Revised prescription issued.', color: 'teal', radius: 'lg' }); }}
          />
        )}
      </Modal>

      {/* Action bar: always reachable, however long the notes get. */}
      <Box style={{ position: 'fixed', left: 0, right: 0, bottom: 16, zIndex: 50, pointerEvents: 'none' }}>
        <Group
          justify="space-between"
          maw="calc(var(--maxw) - 32px)"
          mx="auto"
          px="lg"
          py="sm"
          style={{ pointerEvents: 'auto', borderRadius: 16, background: 'rgb(255 255 255 / 0.92)', backdropFilter: 'blur(14px)', border: '1px solid var(--border)', boxShadow: 'var(--shadow-lg)' }}
        >
          <SaveState update={update} savedAt={savedAt} />
          <Group gap="sm">
            {!form.diagnosis.trim() && <Text size="sm" c="var(--text-muted)" visibleFrom="sm">Add a diagnosis to finish</Text>}
            <Button color="dark" leftSection={<IconCheck size={16} />} disabled={!form.diagnosis.trim()} loading={complete.isPending} onClick={finish}>
              Finish consultation
            </Button>
          </Group>
        </Group>
      </Box>
    </Stack>
  );
}

/** A finished consultation: read-only, with its prescriptions (which can still be revised). */
function Summary({ consultation, appointmentId }) {
  const issue = useIssuePrescription(consultation.id, appointmentId);
  const [revising, setRevising] = useState(null);
  const v = consultation.vitals ?? {};
  const vitals = VITALS.filter(([key]) => v[key] != null);
  return (
    <Stack gap="lg">
      <Panel title="Consultation" subtitle={`Finished ${formatDateTime(consultation.completedAt)}`} right={<IconCircleCheck size={20} color="var(--success)" />}>
        <Stack gap="md">
          {[['Chief complaint', consultation.chiefComplaint], ['Diagnosis', consultation.diagnosis], ['Clinical notes', consultation.notes], ['Advice', consultation.advice],
            ['Follow-up', consultation.followUpDate && formatDate(consultation.followUpDate)]].map(([label, value]) => value && (
            <div key={label}>
              <Text size="xs" c="var(--text-muted)">{label}</Text>
              <Text size="sm" fw={label === 'Diagnosis' ? 600 : 400} style={{ whiteSpace: 'pre-wrap' }}>{value}</Text>
            </div>
          ))}
          {vitals.length > 0 && (
            <Group gap="xl">
              {vitals.map(([key, label, unit]) => (
                <div key={key}>
                  <Text size="xs" c="var(--text-muted)">{label}</Text>
                  <Text className="mono" fw={600}>{v[key]} <Text span size="xs" c="var(--text-subtle)">{unit}</Text></Text>
                </div>
              ))}
            </Group>
          )}
        </Stack>
      </Panel>
      <Panel title="Prescription">
        {consultation.prescriptions.length === 0 ? (
          <Text size="sm" c="var(--text-subtle)">No prescription was issued for this visit.</Text>
        ) : (
          <Stack gap="md">
            {consultation.prescriptions.slice().reverse().map((rx) => <PrescriptionCard key={rx.id} rx={rx} onRevise={setRevising} />)}
          </Stack>
        )}
      </Panel>
      <Modal opened={Boolean(revising)} onClose={() => setRevising(null)} title={<Text fw={600}>Revise {revising?.code}</Text>} size="xl" radius="lg">
        {revising && <PrescriptionBuilder mutation={issue} reviseOf={revising} onCancel={() => setRevising(null)} onDone={() => setRevising(null)} />}
      </Modal>
    </Stack>
  );
}

/** Doctor consult workspace (Design.md §5.5): patient on the left, the visit on the right. */
export function ConsultationWorkspace({ appointmentId }) {
  const consultation = useConsultationForAppointment(appointmentId);

  if (consultation.isPending) {
    return (
      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: 4 }}><Skeleton height={360} radius="xl" /></Grid.Col>
        <Grid.Col span={{ base: 12, md: 8 }}><Skeleton height={520} radius="xl" /></Grid.Col>
      </Grid>
    );
  }
  if (consultation.error?.code === 'NOT_IN_CONSULTATION') {
    return (
      <GlowCard p="xl">
        <EmptyState icon={IconAlertCircle} title="Call the patient in first" action={<Button component={Link} href="/doctor" mt="sm" color="dark">Back to your queue</Button>}>
          A consultation starts when you call the patient in from your queue.
        </EmptyState>
      </GlowCard>
    );
  }
  if (consultation.isError) {
    return <Alert color="red" variant="light" radius="lg" icon={<IconAlertCircle size={18} />}>{friendlyMessage(consultation.error)}</Alert>;
  }

  const c = consultation.data;
  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Button component={Link} href="/doctor" variant="subtle" color="gray" leftSection={<IconArrowLeft size={15} />} px={0}>Today</Button>
        <Text size="sm" c="var(--text-muted)">Opening this record was logged.</Text>
      </Group>
      <Grid gutter="lg" align="flex-start">
        <Grid.Col span={{ base: 12, md: 4 }}>
          <Reveal y={10}><PatientRail consultation={c} /></Reveal>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 8 }}>
          <Reveal y={10} delay={0.05}>
            {c.completed ? <Summary consultation={c} appointmentId={appointmentId} /> : <Editor consultation={c} appointmentId={appointmentId} />}
          </Reveal>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
