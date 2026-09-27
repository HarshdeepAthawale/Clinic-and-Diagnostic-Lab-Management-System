'use client';

import { Alert, Badge, Button, Group, Pagination, SegmentedControl, Skeleton, Stack, Text } from '@mantine/core';
import { IconAlertCircle, IconFileText, IconStethoscope } from '@tabler/icons-react';
import { useState } from 'react';
import { useConsultation, useMyConsultations, useMyPrescriptions, usePrescription, prescriptionPdfUrl } from '@/lib/consultations';
import { friendlyMessage } from '@/lib/errors';
import { formatDate, formatDateTime } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { Panel } from '@/components/ui/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { ListRow } from '@/components/dashboard/widgets/ListRow';
import { ConsultationSummary } from './ConsultationSummary';
import { ConsultWorkspace } from './ConsultWorkspace';

function PageSkeleton() {
  return (
    <Stack gap="lg">
      <Skeleton height={40} width={280} radius="md" />
      <Skeleton height={420} radius="xl" />
    </Stack>
  );
}

function LoadError({ error, what }) {
  return (
    <Alert color="red" variant="light" radius="lg" icon={<IconAlertCircle size={18} />} title={`Couldn't open this ${what}`}>
      {error?.status === 404 ? `This ${what} doesn't exist or isn't yours to see.` : friendlyMessage(error)}
    </Alert>
  );
}

const rxBadge = (code) => (
  <Badge size="sm" radius="sm" variant="outline" color="dark" styles={{ root: { textTransform: 'none', fontFamily: 'var(--font-mono)' } }}>
    {code}
  </Badge>
);

// ------------------------------------------------------------------ doctor

/** Workspace while the consultation is open; read-only summary once it's finished. */
export function DoctorConsultationView({ id }) {
  const consultation = useConsultation(id);
  if (consultation.isPending) return <PageSkeleton />;
  if (consultation.isError) return <LoadError error={consultation.error} what="consultation" />;

  const c = consultation.data;
  if (c.status === 'DRAFT') {
    return (
      <Stack gap="xl">
        <PageTitle
          title="Consultation"
          subtitle={`${c.patient.fullName} · started ${formatDateTime(c.visitAt)}`}
          back={{ href: '/doctor', label: 'Today' }}
        />
        <ConsultWorkspace consultation={c} />
      </Stack>
    );
  }
  return (
    <Stack gap="xl">
      <PageTitle title="Consultation" back={{ href: '/doctor/consultations', label: 'All consultations' }} />
      <ConsultationSummary consultation={c} audience="doctor" />
    </Stack>
  );
}

export function DoctorConsultationsView() {
  const [scope, setScope] = useState('today');
  const [page, setPage] = useState(1);
  const list = useMyConsultations(scope === 'today', page - 1);
  const totalPages = list.data ? Math.ceil(list.data.totalElements / list.data.size) : 0;

  return (
    <Stack gap="xl">
      <PageTitle
        title="Consultations"
        subtitle="Visits you've seen. Open ones stay at the top until you finish them."
        actions={
          <SegmentedControl
            value={scope}
            onChange={(v) => { setScope(v); setPage(1); }}
            data={[{ label: 'Today', value: 'today' }, { label: 'All', value: 'all' }]}
          />
        }
      />
      <GlowCard p="lg">
        {list.isPending ? (
          <Stack gap="xs">{[0, 1, 2].map((i) => <Skeleton key={i} height={52} radius="md" />)}</Stack>
        ) : list.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(list.error)}</Text>
        ) : list.data.content.length === 0 ? (
          <EmptyState icon={IconStethoscope} title={scope === 'today' ? 'No consultations today yet' : 'No consultations yet'}>
            Start one from a checked-in patient on your Today page or the live queue.
          </EmptyState>
        ) : (
          <Stack gap={2}>
            {list.data.content.map((c) => (
              <ListRow
                key={c.id}
                href={`/doctor/consultations/${c.id}`}
                title={c.diagnosis ?? (c.status === 'DRAFT' ? 'Diagnosis not written yet' : '—')}
                subtitle={`${c.patientName} · ${c.patientCode} · ${formatDateTime(c.visitAt)}`}
                right={
                  <Group gap={6} wrap="nowrap">
                    {c.prescriptionCode && rxBadge(c.prescriptionCode)}
                    {c.status === 'DRAFT' ? <StatusBadge status="in-consult" label="In progress" /> : <StatusBadge status="done" label="Finished" />}
                  </Group>
                }
              />
            ))}
          </Stack>
        )}
        {totalPages > 1 && (
          <Group justify="center" mt="md">
            <Pagination total={totalPages} value={page} onChange={setPage} size="sm" color="dark" />
          </Group>
        )}
      </GlowCard>
    </Stack>
  );
}

// ------------------------------------------------------------------ patient

export function PatientPrescriptionsView() {
  const list = useMyPrescriptions();
  return (
    <Stack gap="xl">
      <PageTitle title="Prescriptions" subtitle="Every prescription your doctors have issued, ready to download." />
      <Panel title={list.data ? `${list.data.length} prescription${list.data.length === 1 ? '' : 's'}` : 'Prescriptions'}>
        {list.isPending ? (
          <Stack gap="xs">{[0, 1].map((i) => <Skeleton key={i} height={52} radius="md" />)}</Stack>
        ) : list.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(list.error)}</Text>
        ) : list.data.length === 0 ? (
          <EmptyState icon={IconFileText} title="No prescriptions yet">
            When a doctor prescribes medicines during a visit, the prescription appears here.
          </EmptyState>
        ) : (
          <Stack gap={2}>
            {list.data.map((rx) => (
              <ListRow
                key={rx.id}
                href={`/patient/prescriptions/${rx.id}`}
                title={rx.diagnosis}
                subtitle={`${rx.doctorName} · ${formatDate(rx.issuedAt)} · ${rx.medicineCount} medicine${rx.medicineCount === 1 ? '' : 's'}`}
                right={
                  <Group gap="xs" wrap="nowrap">
                    {rxBadge(rx.prescriptionCode)}
                    <Button
                      component="a"
                      href={prescriptionPdfUrl(rx.id, true)}
                      size="xs"
                      variant="default"
                      onClick={(e) => e.stopPropagation()}
                    >
                      PDF
                    </Button>
                  </Group>
                }
              />
            ))}
          </Stack>
        )}
      </Panel>
    </Stack>
  );
}

export function PatientPrescriptionView({ id }) {
  const rx = usePrescription(id);
  if (rx.isPending) return <PageSkeleton />;
  if (rx.isError) return <LoadError error={rx.error} what="prescription" />;
  return (
    <Stack gap="xl">
      <PageTitle title="Prescription" back={{ href: '/patient/prescriptions', label: 'All prescriptions' }} />
      <ConsultationSummary consultation={rx.data} audience="patient" />
    </Stack>
  );
}
