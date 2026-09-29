'use client';

import { Alert, Button, Grid, Group, Modal, SimpleGrid, Skeleton, Stack, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconEdit, IconFlask, IconKey, IconLink, IconLock, IconUserPlus } from '@tabler/icons-react';
import Link from 'next/link';
import { useState } from 'react';
import { friendlyMessage } from '@/lib/errors';
import { ageGender, formatDate } from '@/lib/format';
import {
  useMyRecord,
  usePatientDetails,
  usePatientRecord,
  useRegisterPatient,
  useReissueRegistrationCode,
  useUpdateDemographics,
} from '@/lib/patients';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { Panel } from '@/components/ui/Panel';
import { Reveal } from '@/components/ui/Reveal';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { FrontDeskActions } from '@/components/appointments/views';
import { OrderTestsDrawer } from '@/components/lab/OrderTestsDrawer';
import { ClinicalEditModal } from './ClinicalEditModal';
import { PatientForm } from './PatientForm';
import { PatientRecordView } from './PatientRecordView';
import { RegistrationSlip } from './RegistrationSlip';

function RecordSkeleton() {
  return (
    <Stack gap="lg">
      <Skeleton height={180} radius="xl" />
      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: 7 }}><Skeleton height={200} radius="xl" /></Grid.Col>
        <Grid.Col span={{ base: 12, md: 5 }}><Skeleton height={200} radius="xl" /></Grid.Col>
      </Grid>
    </Stack>
  );
}

function LoadError({ error, onRetry }) {
  return (
    <Alert color="red" variant="light" radius="lg" icon={<IconAlertCircle size={18} />} title="Couldn't load this record">
      {friendlyMessage(error)}
      {onRetry && (
        <Button size="xs" variant="white" color="red" ml="sm" onClick={onRetry}>Try again</Button>
      )}
    </Alert>
  );
}

// ------------------------------------------------------------------ front desk

export function RegisterPatientView() {
  const register = useRegisterPatient();
  const [result, setResult] = useState(null);

  if (result) {
    return (
      <Stack gap="xl">
        <PageTitle
          title="Patient registered"
          subtitle="Hand the patient this slip. The code links their own login to this record."
          back={{ href: '/reception/patients', label: 'All patients' }}
          actions={
            <>
              <Button variant="default" component={Link} href={`/reception/patients/${result.patient.id}`}>Open record</Button>
              <Button leftSection={<IconUserPlus size={16} />} onClick={() => { register.reset(); setResult(null); }}>
                Register another
              </Button>
            </>
          }
        />
        <RegistrationSlip patient={result.patient} code={result.registrationCode} expiresAt={result.registrationCodeExpiresAt} />
      </Stack>
    );
  }

  return (
    <Stack gap="xl">
      <PageTitle
        title="Register a patient"
        subtitle="One record, shared by the clinic and the lab. Search first to avoid creating a duplicate."
        back={{ href: '/reception/patients', label: 'Search patients' }}
      />
      <Reveal delay={0.05}>
        <GlowCard p="xl">
          <PatientForm
            mode="register"
            submitLabel="Register patient"
            submitting={register.isPending}
            error={register.error}
            onSubmit={(body, { setErrors }) =>
              register.mutate(body, {
                onSuccess: setResult,
                onError: (error) => error.fields && setErrors(error.fields),
              })
            }
          />
        </GlowCard>
      </Reveal>
    </Stack>
  );
}

export function ReceptionPatientView({ id }) {
  const details = usePatientDetails(id);
  const update = useUpdateDemographics(id);
  const reissue = useReissueRegistrationCode(id);
  const [editOpened, edit] = useDisclosure(false);
  const [slip, setSlip] = useState(null);
  const p = details.data;

  if (details.isPending) return <RecordSkeleton />;
  if (details.isError) return <LoadError error={details.error} onRetry={() => details.refetch()} />;

  const codeActive = p.registrationCodeExpiresAt && new Date(p.registrationCodeExpiresAt) > new Date();

  return (
    <Stack gap="xl">
      <PageTitle
        title={p.fullName}
        subtitle={<span className="mono">{p.patientCode} · {ageGender(p.age, p.gender)} · born {formatDate(p.dob)}</span>}
        back={{ href: '/reception/patients', label: 'All patients' }}
        actions={
          <>
            <Button variant="default" leftSection={<IconEdit size={16} />} onClick={edit.open}>Edit details</Button>
            <FrontDeskActions patient={{ id: p.id, fullName: p.fullName, patientCode: p.patientCode, age: p.age, gender: p.gender, maskedPhone: '' }} />
          </>
        }
      />

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: 7 }}>
          <Reveal delay={0.05}>
            <Panel title="Contact & identity" subtitle={`Registered ${formatDate(p.createdAt)}`}>
              <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="lg">
                {[
                  ['Phone', p.phone, true],
                  ['Address', p.address],
                  ['Emergency contact', p.emergencyContactName],
                  ['Emergency phone', p.emergencyContactPhone, true],
                ].map(([label, value, mono]) => (
                  <div key={label}>
                    <Text size="xs" c="var(--text-muted)">{label}</Text>
                    <Text size="sm" fw={500} className={mono ? 'mono' : undefined}>
                      {value || <Text span c="var(--text-subtle)">Not recorded</Text>}
                    </Text>
                  </div>
                ))}
              </SimpleGrid>
              <Text size="xs" c="var(--text-subtle)" mt="lg">
                Medical details (allergies, history) are visible to the patient&apos;s doctors, not the front desk.
              </Text>
            </Panel>
          </Reveal>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 5 }}>
          <Reveal delay={0.1}>
            <Panel title="Patient login">
              {p.hasLogin ? (
                <Group gap="sm">
                  <StatusBadge status="verified" label="Linked" />
                  <Text size="sm" c="var(--text-muted)">The patient can see this record online.</Text>
                </Group>
              ) : (
                <Stack gap="sm">
                  <Group gap="sm">
                    <StatusBadge status="pending" label="Not linked yet" />
                    <Text size="sm" c="var(--text-muted)">
                      {codeActive ? `Code valid until ${formatDate(p.registrationCodeExpiresAt)}` : 'No valid code'}
                    </Text>
                  </Group>
                  <Text size="sm" c="var(--text-muted)">
                    Lost the slip? Issuing a new code cancels the old one.
                  </Text>
                  <Button
                    variant="default"
                    leftSection={<IconKey size={16} />}
                    loading={reissue.isPending}
                    onClick={() => reissue.mutate(undefined, { onSuccess: setSlip })}
                  >
                    Issue new registration code
                  </Button>
                </Stack>
              )}
            </Panel>
          </Reveal>
        </Grid.Col>
      </Grid>

      <Modal opened={editOpened} onClose={edit.close} title={<Text fw={600}>Edit patient details</Text>} size="xl">
        <PatientForm
          mode="edit"
          initialValues={{
            fullName: p.fullName, dob: p.dob, gender: p.gender, phone: p.phone, address: p.address ?? '',
            emergencyContactName: p.emergencyContactName ?? '', emergencyContactPhone: p.emergencyContactPhone ?? '',
          }}
          submitLabel="Save changes"
          submitting={update.isPending}
          error={update.error}
          onSubmit={(body, { setErrors }) =>
            update.mutate(body, {
              onSuccess: () => {
                notifications.show({ title: 'Details saved', message: `${body.fullName}'s details were updated.`, color: 'teal', radius: 'lg' });
                edit.close();
              },
              onError: (error) => error.fields && setErrors(error.fields),
            })
          }
        />
      </Modal>

      <Modal opened={Boolean(slip)} onClose={() => setSlip(null)} title={<Text fw={600}>New registration code</Text>} size="lg">
        {slip && <RegistrationSlip patient={slip.patient} code={slip.registrationCode} expiresAt={slip.registrationCodeExpiresAt} />}
      </Modal>
    </Stack>
  );
}

// ------------------------------------------------------------------ doctor

export function DoctorPatientView({ id }) {
  const record = usePatientRecord(id);
  const [editOpened, edit] = useDisclosure(false);
  const [ordering, order] = useDisclosure(false);

  if (record.isPending) return <RecordSkeleton />;
  if (record.error?.code === 'NO_CARE_RELATIONSHIP') {
    return (
      <Stack gap="xl">
        <PageTitle title="Record locked" back={{ href: '/doctor/patients', label: 'Search patients' }} />
        <GlowCard p="xl">
          <EmptyState icon={IconLock} title="You don't have an appointment with this patient">
            Full medical records open only for patients you are treating. Ask the front desk to book this patient
            with you or issue a walk-in token — the record opens as soon as the appointment exists.
          </EmptyState>
        </GlowCard>
      </Stack>
    );
  }
  if (record.isError) return <LoadError error={record.error} />;

  return (
    <Stack gap="xl">
      <PageTitle
        title="Medical record"
        subtitle="Opening this record was logged."
        back={{ href: '/doctor/patients', label: 'Search patients' }}
      />
      <PatientRecordView
        record={record.data}
        withTrends
        actions={
          <Group gap="sm">
            <Button variant="default" leftSection={<IconEdit size={16} />} onClick={edit.open}>Update clinical details</Button>
            <Button leftSection={<IconFlask size={16} />} onClick={order.open}>Order tests</Button>
          </Group>
        }
      />
      {editOpened && <ClinicalEditModal record={record.data} opened={editOpened} onClose={edit.close} />}
      <OrderTestsDrawer opened={ordering} onClose={order.close} patient={record.data} />
    </Stack>
  );
}

// ------------------------------------------------------------------ patient

export function MyRecordView() {
  const record = useMyRecord();
  if (record.isPending) return <RecordSkeleton />;
  if (record.isError) return <LoadError error={record.error} onRetry={() => record.refetch()} />;
  return (
    <Stack gap="xl">
      <PageTitle title="My record" subtitle="Your details as the clinic has them. Something wrong? Tell the front desk at your next visit." />
      <PatientRecordView record={record.data} audience="patient" />
      <Group gap={8}>
        <IconLink size={15} color="var(--text-subtle)" />
        <Text size="xs" c="var(--text-subtle)">Only you and doctors treating you can see this record. Every doctor view is logged.</Text>
      </Group>
    </Stack>
  );
}
