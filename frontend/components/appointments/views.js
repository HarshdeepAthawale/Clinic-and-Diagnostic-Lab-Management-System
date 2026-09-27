'use client';

import { Alert, Badge, Box, Button, Group, Modal, Select, SimpleGrid, Skeleton, Stack, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconAlertCircle, IconArrowsMaximize, IconCalendarPlus, IconCalendarOff, IconCircleCheck, IconTicket, IconX } from '@tabler/icons-react';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import {
  addDays,
  clinicDate,
  dayLabel,
  useAppointments,
  useDoctors,
  useMyAppointments,
  useMyDoctorProfile,
  useQueue,
  useWorkingHours,
  weekStart,
} from '@/lib/appointments';
import { friendlyMessage } from '@/lib/errors';
import { APPOINTMENT_STATUS, CLINIC_TIME_ZONE, formatDateTime, formatRelative, formatTime } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { Panel } from '@/components/ui/Panel';
import { Reveal } from '@/components/ui/Reveal';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { AppointmentActions } from './AppointmentActions';
import { BookingForm } from './BookingForm';
import { DateNav, DayAgenda, StatusSummary } from './DayAgenda';
import { IssueTokenForm } from './IssueTokenForm';
import { QueueBoard, queueClasses } from './QueueBoard';
import { TokenSlip } from './TokenSlip';
import { WorkingHoursEditor } from './WorkingHoursEditor';

function LoadError({ error, onRetry }) {
  return (
    <Alert color="red" variant="light" radius="lg" icon={<IconAlertCircle size={18} />} title="Couldn't load appointments">
      {friendlyMessage(error)}
      {onRetry && <Button size="xs" variant="white" color="red" ml="sm" onClick={onRetry}>Try again</Button>}
    </Alert>
  );
}

// ------------------------------------------------------------------ front desk: book / token modals

/** "Book appointment" and "Walk-in token" buttons with their modals. Reused on several pages. */
export function FrontDeskActions({ patient = null, size = 'sm' }) {
  const [bookOpen, book] = useDisclosure(false);
  const [tokenOpen, token] = useDisclosure(false);
  const [issued, setIssued] = useState(null);
  const [booked, setBooked] = useState(null);

  const closeToken = () => { token.close(); setIssued(null); };
  const closeBook = () => { book.close(); setBooked(null); };

  return (
    <>
      <Button size={size} variant="default" leftSection={<IconTicket size={16} />} onClick={token.open}>Walk-in token</Button>
      <Button size={size} color="dark" leftSection={<IconCalendarPlus size={16} />} onClick={book.open}>Book appointment</Button>

      <Modal opened={tokenOpen} onClose={closeToken} title={<Text fw={600}>{issued ? 'Token issued' : 'Walk-in token'}</Text>} size="lg" radius="lg">
        {issued ? <TokenSlip appointment={issued} onDone={closeToken} /> : <IssueTokenForm initialPatient={patient} onIssued={setIssued} />}
      </Modal>

      <Modal opened={bookOpen} onClose={closeBook} title={<Text fw={600}>{booked ? 'Appointment booked' : 'Book an appointment'}</Text>} size="xl" radius="lg">
        {booked ? (
          <Stack align="center" py="lg" gap="sm">
            <IconCircleCheck size={44} color="var(--success)" stroke={1.5} />
            <Text fw={600}>{booked.patient.fullName} with {booked.doctor.fullName}</Text>
            <Text c="var(--text-muted)">{formatDateTime(booked.scheduledAt)}</Text>
            <Button color="dark" mt="sm" onClick={closeBook}>Done</Button>
          </Stack>
        ) : (
          <BookingForm role="RECEPTIONIST" initialPatient={patient} onBooked={setBooked} />
        )}
      </Modal>
    </>
  );
}

// ------------------------------------------------------------------ front desk: appointments

export function ReceptionAppointmentsView() {
  const [date, setDate] = useState(clinicDate());
  const [doctorId, setDoctorId] = useState(null);
  const doctors = useDoctors();
  const list = useAppointments({ from: date, to: date, doctorId }, { live: date === clinicDate() });

  return (
    <Stack gap="xl">
      <PageTitle title="Appointments" subtitle="Check patients in as they arrive — they join the live queue with a token." actions={<FrontDeskActions />} />
      <Reveal delay={0.05}>
        <GlowCard p="lg">
          <Group justify="space-between" mb="md" wrap="wrap" gap="md">
            <DateNav date={date} onChange={setDate} />
            <Select
              placeholder="All doctors"
              clearable
              w={240}
              radius="md"
              data={(doctors.data ?? []).map((d) => ({ value: d.id, label: d.fullName }))}
              value={doctorId}
              onChange={setDoctorId}
              aria-label="Filter by doctor"
            />
          </Group>
          {list.data && <Box mb="md"><StatusSummary appointments={list.data} /></Box>}
          {list.isPending ? (
            <Skeleton height={240} radius="md" />
          ) : list.isError ? (
            <LoadError error={list.error} onRetry={() => list.refetch()} />
          ) : (
            <DayAgenda appointments={list.data} role="RECEPTIONIST" patientHref={(id) => `/reception/patients/${id}`} />
          )}
        </GlowCard>
      </Reveal>
    </Stack>
  );
}

// ------------------------------------------------------------------ front desk / doctor / admin: live queue

function LiveStamp({ updatedAt }) {
  return (
    <Group gap={8}>
      <Box w={8} h={8} style={{ borderRadius: 999, background: 'var(--success)', boxShadow: '0 0 0 4px var(--success-soft)' }} />
      <Text size="sm" c="var(--text-muted)">Live · updated {formatRelative(updatedAt)}</Text>
    </Group>
  );
}

export function QueueView({ role }) {
  const queue = useQueue();
  const [tv, setTv] = useState(false);

  const enterTv = () => {
    setTv(true);
    // Fullscreen can be refused (iframe, browser setting); the TV layout still covers the window.
    document.documentElement.requestFullscreen?.().catch(() => {});
  };
  const exitTv = () => {
    setTv(false);
    if (document.fullscreenElement) document.exitFullscreen?.().catch(() => {});
  };

  // Leaving fullscreen with Esc also leaves TV mode.
  useEffect(() => {
    const onChange = () => !document.fullscreenElement && setTv(false);
    document.addEventListener('fullscreenchange', onChange);
    return () => document.removeEventListener('fullscreenchange', onChange);
  }, []);

  const waiting = queue.data?.doctors.reduce((n, c) => n + c.waiting.length, 0) ?? 0;

  return (
    <Stack gap="xl">
      <PageTitle
        title="Live queue"
        subtitle={role === 'RECEPTIONIST' ? 'Everyone checked in today, per doctor, in token order.' : 'Everyone checked in today, per doctor.'}
        actions={
          <>
            <Button variant="default" leftSection={<IconArrowsMaximize size={16} />} onClick={enterTv}>Waiting-room screen</Button>
            {role === 'RECEPTIONIST' && <FrontDeskActions />}
          </>
        }
      />
      {queue.data && (
        <Group gap="xl">
          <LiveStamp updatedAt={queue.data.generatedAt} />
          <Text size="sm" c="var(--text-muted)"><Text span fw={600} className="mono" c="var(--text)">{waiting}</Text> waiting</Text>
        </Group>
      )}
      {queue.isPending ? (
        <SimpleGrid cols={{ base: 1, md: 2, lg: 3 }}>{[0, 1, 2].map((i) => <Skeleton key={i} height={320} radius="lg" />)}</SimpleGrid>
      ) : queue.isError ? (
        <LoadError error={queue.error} onRetry={() => queue.refetch()} />
      ) : (
        <QueueBoard board={queue.data} role={role} />
      )}

      {/* Waiting-room screen: tokens only, never patient names. */}
      {tv && queue.data && (
        <div className={queueClasses.tv} style={{ position: 'fixed', inset: 0, zIndex: 300, overflow: 'auto' }}>
          <Group justify="space-between" mb={32}>
            <div>
              <Text fz={40} fw={700} style={{ letterSpacing: '-0.03em' }}>Please wait for your token</Text>
              <LiveStamp updatedAt={queue.data.generatedAt} />
            </div>
            <Button variant="default" leftSection={<IconX size={16} />} onClick={exitTv}>Close</Button>
          </Group>
          <QueueBoard board={queue.data} role="ADMIN" tv />
        </div>
      )}
    </Stack>
  );
}

// ------------------------------------------------------------------ doctor: schedule

function WeekStrip({ date, onChange, doctorId }) {
  const start = weekStart(date);
  const end = addDays(start, 6);
  const week = useAppointments({ from: start, to: end, doctorId });
  const today = clinicDate();
  const days = Array.from({ length: 7 }, (_, i) => addDays(start, i));
  const dayOf = (iso) => new Intl.DateTimeFormat('en-CA', { timeZone: CLINIC_TIME_ZONE }).format(new Date(iso));

  return (
    <SimpleGrid cols={7} spacing={8}>
      {days.map((d) => {
        const count = week.data?.filter((a) => dayOf(a.scheduledAt) === d && a.status !== 'CANCELLED').length ?? 0;
        const active = d === date;
        return (
          <Box
            key={d}
            component="button"
            type="button"
            onClick={() => onChange(d)}
            p={10}
            style={{
              borderRadius: 'var(--radius-md)',
              border: `1px solid ${active ? 'var(--ink)' : 'var(--border)'}`,
              background: active ? 'var(--ink)' : 'var(--surface)',
              color: active ? 'var(--on-ink)' : 'var(--text)',
              textAlign: 'left',
              cursor: 'pointer',
            }}
          >
            <Text size="xs" fw={600} style={{ opacity: 0.7 }}>{d === today ? 'Today' : dayLabel(d, { weekday: 'short' })}</Text>
            <Text fw={600} className="mono">{Number(d.slice(8))}</Text>
            <Text size="xs" style={{ opacity: 0.75 }}>{count ? `${count} visit${count === 1 ? '' : 's'}` : '—'}</Text>
          </Box>
        );
      })}
    </SimpleGrid>
  );
}

export function DoctorScheduleView() {
  const me = useMyDoctorProfile();
  const [date, setDate] = useState(clinicDate());
  const doctorId = me.data?.id;
  const day = useAppointments({ from: date, to: date, doctorId }, { live: date === clinicDate() });
  const hours = useWorkingHours(doctorId);

  if (me.isError) return <LoadError error={me.error} />;

  return (
    <Stack gap="xl">
      <PageTitle title="Schedule" subtitle="Your booked visits and walk-ins. Opening a patient from here shows their full record." />
      {me.data && !me.data.hasWorkingHours && (
        <Alert color="yellow" variant="light" radius="lg" icon={<IconCalendarOff size={18} />} title="No working hours yet">
          Patients can&apos;t book you until you set your hours below. Walk-in tokens still work.
        </Alert>
      )}
      <Reveal delay={0.05}>
        <GlowCard p="lg">
          <Stack gap="md">
            <Group justify="space-between" wrap="wrap">
              <DateNav date={date} onChange={setDate} />
              {day.data && <StatusSummary appointments={day.data} />}
            </Group>
            {doctorId && <WeekStrip date={date} onChange={setDate} doctorId={doctorId} />}
            {day.isPending || !doctorId ? (
              <Skeleton height={200} radius="md" />
            ) : day.isError ? (
              <LoadError error={day.error} onRetry={() => day.refetch()} />
            ) : (
              <DayAgenda appointments={day.data} role="DOCTOR" showDoctor={false} patientHref={(id) => `/doctor/patients/${id}`} />
            )}
          </Stack>
        </GlowCard>
      </Reveal>
      <Reveal delay={0.1}>
        <Panel title="Working hours" subtitle="When patients can book you">
          {hours.isPending || !doctorId ? <Skeleton height={300} radius="md" /> : <WorkingHoursEditor key={doctorId} doctorId={doctorId} blocks={hours.data} />}
        </Panel>
      </Reveal>
    </Stack>
  );
}

// ------------------------------------------------------------------ patient

function UpcomingCard({ a }) {
  return (
    <GlowCard p="lg">
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <div style={{ minWidth: 0 }}>
          <Text size="xs" c="var(--text-muted)" fw={600} tt="uppercase" style={{ letterSpacing: '0.06em' }}>
            {dayLabel(new Intl.DateTimeFormat('en-CA', { timeZone: CLINIC_TIME_ZONE }).format(new Date(a.scheduledAt)), { weekday: 'long', day: 'numeric', month: 'long' })}
          </Text>
          <Text fz={32} fw={600} className="mono" lh={1.2} mt={4}>{formatTime(a.scheduledAt)}</Text>
          <Text fw={600} mt={6}>{a.doctor.fullName}</Text>
          <Text size="sm" c="var(--text-muted)">{a.doctor.specialization}{a.reason ? ` · ${a.reason}` : ''}</Text>
        </div>
        <Stack align="flex-end" gap="sm">
          <StatusBadge status={APPOINTMENT_STATUS[a.status]} />
          {a.queueToken && <Badge variant="outline" color="dark" radius="sm" className="mono">{a.queueToken}</Badge>}
        </Stack>
      </Group>
      <Group justify="flex-end" mt="md">
        <AppointmentActions role="PATIENT" appointment={a} size="sm" />
      </Group>
    </GlowCard>
  );
}

export function PatientAppointmentsView() {
  const mine = useMyAppointments();
  return (
    <Stack gap="xl">
      <PageTitle
        title="Appointments"
        subtitle="Book a visit, see what's coming up, cancel if plans change."
        actions={<Button component={Link} href="/patient/appointments/book" leftSection={<IconCalendarPlus size={16} />}>Book appointment</Button>}
      />
      {mine.isPending ? (
        <Skeleton height={200} radius="lg" />
      ) : mine.isError ? (
        <LoadError error={mine.error} onRetry={() => mine.refetch()} />
      ) : (
        <>
          {mine.data.upcoming.length === 0 ? (
            <GlowCard p="xl">
              <EmptyState
                icon={IconCalendarPlus}
                title="No upcoming appointments"
                action={<Button component={Link} href="/patient/appointments/book" mt="sm">Book one now</Button>}
              >
                Pick a doctor and a time that suits you — it takes under a minute.
              </EmptyState>
            </GlowCard>
          ) : (
            <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
              {mine.data.upcoming.map((a, i) => (
                <Reveal key={a.id} delay={0.04 * i}><UpcomingCard a={a} /></Reveal>
              ))}
            </SimpleGrid>
          )}
          <Panel title="Past visits" subtitle={`${mine.data.past.length} visit${mine.data.past.length === 1 ? '' : 's'}`}>
            <DayAgenda
              appointments={mine.data.past}
              role="PATIENT"
              showDate
              emptyTitle="No past visits yet"
              emptyText="Visits you've had at the clinic will be listed here."
            />
          </Panel>
        </>
      )}
    </Stack>
  );
}

export function PatientBookView() {
  const [booked, setBooked] = useState(null);
  if (booked) {
    return (
      <Stack gap="xl">
        <PageTitle title="You're booked" back={{ href: '/patient/appointments', label: 'My appointments' }} />
        <GlowCard p="xl">
          <Stack align="center" gap="sm" py="lg">
            <IconCircleCheck size={52} color="var(--success)" stroke={1.4} />
            <Text fz={28} fw={600} className="mono">{formatTime(booked.scheduledAt)}</Text>
            <Text fw={600}>{booked.doctor.fullName} · {booked.doctor.specialization}</Text>
            <Text c="var(--text-muted)">{formatDateTime(booked.scheduledAt)}</Text>
            <Text size="sm" c="var(--text-muted)" maw={420} ta="center" mt="sm">
              We&apos;ll email you a reminder the day before. At the clinic, check in at the front desk to get your queue token.
            </Text>
            <Group mt="md">
              <Button variant="default" component={Link} href="/patient/appointments">My appointments</Button>
              <Button color="dark" onClick={() => setBooked(null)}>Book another</Button>
            </Group>
          </Stack>
        </GlowCard>
      </Stack>
    );
  }
  return (
    <Stack gap="xl">
      <PageTitle title="Book an appointment" back={{ href: '/patient/appointments', label: 'My appointments' }} />
      <Reveal delay={0.05}>
        <GlowCard p="xl">
          <BookingForm role="PATIENT" onBooked={setBooked} />
        </GlowCard>
      </Reveal>
    </Stack>
  );
}
