'use client';

import { Alert, Button, Grid, Group, Kbd, Loader, Modal, Stack, Text, TextInput, Textarea, UnstyledButton } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDebouncedValue, useHotkeys } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconCheck, IconCloudCheck, IconLock } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { useCompleteConsultation, useSaveConsultation } from '@/lib/consultations';
import { friendlyMessage } from '@/lib/errors';
import { formatTime } from '@/lib/format';
import { Panel } from '@/components/ui/Panel';
import { Reveal } from '@/components/ui/Reveal';
import { PatientRail } from './PatientRail';
import { PrescriptionBuilder } from './PrescriptionBuilder';
import { FOLLOW_UPS, formValuesFrom, isCompleteMedicine, isPartialMedicine, toRequest } from './presets';
import { VitalsInput } from './VitalsInput';

const AUTOSAVE_MS = 1200;

function inDays(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

function SaveIndicator({ state, savedAt }) {
  if (state === 'saving') {
    return (
      <Group gap={6}><Loader size={12} color="gray" /><Text size="xs" c="var(--text-muted)">Saving…</Text></Group>
    );
  }
  if (state === 'error') {
    return (
      <Group gap={6}><IconAlertCircle size={14} color="var(--critical)" /><Text size="xs" c="var(--critical)" fw={600}>Not saved — check the highlighted fields</Text></Group>
    );
  }
  if (state === 'dirty') return <Text size="xs" c="var(--text-subtle)">Unsaved changes</Text>;
  return (
    <Group gap={6}>
      <IconCloudCheck size={15} color="var(--success)" />
      <Text size="xs" c="var(--text-muted)">{savedAt ? `Saved ${formatTime(savedAt)}` : 'All changes saved'}</Text>
    </Group>
  );
}

/** Server field errors ("medicines[0].frequency", "vitals.spo2Percent") → form paths. */
function toFormErrors(fields) {
  return Object.fromEntries(Object.entries(fields ?? {}).map(([k, v]) => [k.replace(/\[(\d+)\]/g, '.$1'), v]));
}

/**
 * The consult workspace (Design.md §5.5): patient rail on the left; vitals, complaint, notes,
 * diagnosis, prescription, advice and follow-up on the right. Drafts autosave; finishing issues the
 * prescription and completes the visit.
 */
export function ConsultWorkspace({ consultation, onCompleted }) {
  const save = useSaveConsultation(consultation.id);
  const complete = useCompleteConsultation(consultation.id);
  const [confirming, setConfirming] = useState(false);
  const form = useForm({ initialValues: formValuesFrom(consultation) });

  const body = JSON.stringify(toRequest(form.values));
  const [debouncedBody] = useDebouncedValue(body, AUTOSAVE_MS);
  // What the server has: the last body it accepted, and when. Saving/failed come from the mutation.
  const [saved, setSaved] = useState(() => ({
    at: consultation.updatedAt,
    body: JSON.stringify(toRequest(formValuesFrom(consultation))),
  }));

  const persist = (payload) =>
    save.mutate(JSON.parse(payload), {
      onSuccess: (result) => setSaved({ at: result.updatedAt, body: payload }),
      onError: (error) => form.setErrors(toFormErrors(error.fields)),
    });

  // Autosave once typing pauses.
  useEffect(() => {
    if (debouncedBody !== saved.body && !save.isPending && !complete.isPending) persist(debouncedBody);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedBody]);

  const displayState = save.isPending ? 'saving' : save.isError ? 'error' : body !== saved.body ? 'dirty' : 'saved';

  const partialRows = form.values.medicines.filter(isPartialMedicine).length;
  const medicineCount = form.values.medicines.filter(isCompleteMedicine).length;

  const openFinish = () => {
    if (!form.values.diagnosis.trim()) {
      form.setFieldError('diagnosis', 'Add a diagnosis before finishing');
      return;
    }
    setConfirming(true);
  };

  const finish = () =>
    complete.mutate(toRequest(form.values), {
      onSuccess: (done) => {
        setConfirming(false);
        notifications.show({
          title: 'Consultation finished',
          message: done.prescription
            ? `Prescription ${done.prescription.prescriptionCode} issued to ${done.patient.fullName}.`
            : `${done.patient.fullName}'s visit is complete.`,
          color: 'teal',
          radius: 'lg',
          icon: <IconCheck size={18} />,
        });
        onCompleted?.(done);
      },
      onError: (error) => {
        form.setErrors(toFormErrors(error.fields));
        if (error.code === 'DIAGNOSIS_REQUIRED') form.setFieldError('diagnosis', 'Add a diagnosis before finishing');
      },
    });

  useHotkeys([
    ['mod+S', () => body !== saved.body && persist(body)],
    ['mod+Enter', openFinish],
  ], []);

  return (
    <Grid gutter="lg">
      <Grid.Col span={{ base: 12, lg: 4 }}>
        <PatientRail patient={consultation.patient} consultationId={consultation.id} />
      </Grid.Col>

      <Grid.Col span={{ base: 12, lg: 8 }}>
        <Stack gap="lg">
          <Reveal y={8}>
            <Group justify="space-between" wrap="wrap" gap="sm">
              <SaveIndicator state={displayState} savedAt={saved.at} />
              <Group gap="sm">
                <Text size="xs" c="var(--text-subtle)" visibleFrom="sm">
                  <Kbd size="xs">Ctrl</Kbd> + <Kbd size="xs">Enter</Kbd> to finish
                </Text>
                <Button onClick={openFinish} leftSection={<IconLock size={16} />}>Finish consultation</Button>
              </Group>
            </Group>
          </Reveal>

          <Reveal delay={0.03}>
            <Panel title="Vitals" subtitle="Optional">
              <VitalsInput form={form} />
            </Panel>
          </Reveal>

          <Reveal delay={0.06}>
            <Panel title="Assessment">
              <Stack gap="md">
                <TextInput label="Chief complaint" placeholder="e.g. Fever and sore throat for 3 days" maxLength={500} {...form.getInputProps('chiefComplaint')} />
                <Textarea
                  label="Clinical notes"
                  description="Examination findings and reasoning. Visible to clinicians only, not to the patient."
                  autosize
                  minRows={4}
                  maxLength={20000}
                  {...form.getInputProps('notes')}
                />
                <TextInput label="Diagnosis" placeholder="e.g. Acute pharyngitis" required maxLength={1000} {...form.getInputProps('diagnosis')} />
              </Stack>
            </Panel>
          </Reveal>

          <Reveal delay={0.09}>
            <Panel title="Prescription" subtitle={medicineCount ? `${medicineCount} medicine${medicineCount === 1 ? '' : 's'}` : 'Numbered and locked when you finish'}>
              <PrescriptionBuilder form={form} />
            </Panel>
          </Reveal>

          <Reveal delay={0.12}>
            <Panel title="Advice & follow-up">
              <Stack gap="md">
                <Textarea label="Advice for the patient" placeholder="Diet, rest, warning signs to come back for…" autosize minRows={2} maxLength={4000} {...form.getInputProps('advice')} />
                <Group align="flex-end" gap="sm" wrap="wrap">
                  <TextInput label="Follow-up date" type="date" min={inDays(0)} {...form.getInputProps('followUpDate')} />
                  {FOLLOW_UPS.map((f) => (
                    <UnstyledButton
                      key={f.label}
                      onClick={() => form.setFieldValue('followUpDate', inDays(f.days))}
                      style={{ fontSize: 13, fontWeight: 600, padding: '7px 12px', borderRadius: 999, border: '1px solid var(--border-strong)', background: form.values.followUpDate === inDays(f.days) ? 'var(--accent-soft)' : 'var(--surface)', color: form.values.followUpDate === inDays(f.days) ? 'var(--accent)' : 'var(--text)' }}
                    >
                      {f.label}
                    </UnstyledButton>
                  ))}
                  {form.values.followUpDate && (
                    <UnstyledButton onClick={() => form.setFieldValue('followUpDate', '')} style={{ fontSize: 13, color: 'var(--text-muted)', padding: '7px 4px' }}>
                      Clear
                    </UnstyledButton>
                  )}
                </Group>
              </Stack>
            </Panel>
          </Reveal>
        </Stack>
      </Grid.Col>

      <Modal opened={confirming} onClose={() => setConfirming(false)} title={<Text fw={600}>Finish this consultation?</Text>} size="md">
        <Stack gap="md">
          {complete.error && !complete.error.fields && (
            <Alert color="red" variant="light" radius="md">{friendlyMessage(complete.error)}</Alert>
          )}
          <Text size="sm">
            <b>{consultation.patient.fullName}</b> · {form.values.diagnosis.trim()}
          </Text>
          <Text size="sm" c="var(--text-muted)">
            {medicineCount
              ? `A prescription with ${medicineCount} medicine${medicineCount === 1 ? '' : 's'} will be issued and numbered.`
              : 'No prescription will be issued — there are no medicines.'}{' '}
            The record is locked afterwards and the visit is marked complete.
          </Text>
          {partialRows > 0 && (
            <Alert color="yellow" variant="light" radius="md" icon={<IconAlertCircle size={16} />}>
              {partialRows} unfinished medicine row{partialRows === 1 ? '' : 's'} will be left out. Go back to complete {partialRows === 1 ? 'it' : 'them'} if needed.
            </Alert>
          )}
          <Group justify="flex-end" gap="sm">
            <Button variant="default" onClick={() => setConfirming(false)}>Keep editing</Button>
            <Button onClick={finish} loading={complete.isPending} leftSection={<IconLock size={16} />}>
              {medicineCount ? 'Finish and issue prescription' : 'Finish consultation'}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Grid>
  );
}
