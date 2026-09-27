'use client';

import { Avatar, Box, Group, Progress, SimpleGrid, Stack, Text } from '@mantine/core';
import {
  IconActivity,
  IconCalendarEvent,
  IconCalendarOff,
  IconClockHour4,
  IconDropletFilled,
  IconFileSearch,
  IconHistory,
  IconLink,
  IconUserPlus,
  IconUsers,
} from '@tabler/icons-react';
import Link from 'next/link';
import { ageGender, APPOINTMENT_STATUS, formatDateTime, formatRelative, formatTime } from '@/lib/format';
import { roleConfig } from '@/lib/roles';
import { EmptyState } from '@/components/ui/EmptyState';
import { KpiTile } from '@/components/ui/KpiTile';
import { Panel } from '@/components/ui/Panel';
import { RoleBadge } from '@/components/ui/RoleBadge';
import { SafetyBanner } from '@/components/ui/SafetyBanner';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { initials } from '@/components/shell/UserMenu';
import { QueueBoard } from '@/components/appointments/QueueBoard';
import { OpenConsultation, RecentPrescriptions } from './consultationWidgets';
import { ListRow } from './ListRow';

/** Where a staff member opens a patient's page, if their role has one. */
function patientHref(role, id) {
  if (role === 'DOCTOR') return `/doctor/patients/${id}`;
  if (role === 'RECEPTIONIST') return `/reception/patients/${id}`;
  return null;
}

const STAT_ICONS = {
  appointmentsToday: IconCalendarEvent,
  seenToday: IconActivity,
  waiting: IconClockHour4,
  underCare: IconUsers,
  registeredToday: IconUserPlus,
  totalPatients: IconUsers,
  pendingCodes: IconLink,
  recordOpensToday: IconFileSearch,
  activeStaff: IconUsers,
};

function Stats({ widget }) {
  return (
    <SimpleGrid cols={{ base: 2, md: 4 }} spacing="lg">
      {widget.data.map((stat) => (
        <KpiTile key={stat.key} label={stat.label} value={stat.value} caption={stat.hint} icon={STAT_ICONS[stat.key]} />
      ))}
    </SimpleGrid>
  );
}

function Schedule({ widget, role }) {
  const showDoctor = role !== 'DOCTOR';
  return (
    <Panel title={widget.title} subtitle={`${widget.data.length} appointment${widget.data.length === 1 ? '' : 's'}`}>
      {widget.data.length === 0 ? (
        <EmptyState icon={IconCalendarOff} title="No appointments today" compact>
          Booked visits and walk-in tokens appear here as they are added.
        </EmptyState>
      ) : (
        <Stack gap={2}>
          {widget.data.map((row) => (
            <ListRow
              key={row.appointmentId}
              href={patientHref(role, row.patient.id)}
              leading={
                <Text className="mono" size="sm" fw={500} w={72} c="var(--text-muted)" style={{ whiteSpace: 'nowrap' }}>
                  {formatTime(row.scheduledAt)}
                </Text>
              }
              title={`${row.patient.fullName} · ${ageGender(row.patient.age, row.patient.gender)}`}
              subtitle={[row.queueToken, row.patient.patientCode, showDoctor ? row.doctorName : null].filter(Boolean).join(' · ')}
              right={<StatusBadge status={APPOINTMENT_STATUS[row.status]} />}
            />
          ))}
        </Stack>
      )}
    </Panel>
  );
}

function RecentRecords({ widget, role }) {
  return (
    <Panel title={widget.title} subtitle="Every record you open is logged">
      {widget.data.length === 0 ? (
        <EmptyState icon={IconHistory} title="No records opened yet" compact>
          Patients you open from your schedule or search show up here.
        </EmptyState>
      ) : (
        <Stack gap={2}>
          {widget.data.map((row) => (
            <ListRow
              key={row.accessedAt + row.patientId}
              href={patientHref(role, row.patientId)}
              title={row.patientName}
              subtitle={`${row.patientCode} · ${formatRelative(row.accessedAt)}`}
            />
          ))}
        </Stack>
      )}
    </Panel>
  );
}

function RecentPatients({ widget, role }) {
  return (
    <Panel title={widget.title} right={<Link href="/reception/patients" style={{ fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>All patients</Link>}>
      {widget.data.length === 0 ? (
        <EmptyState icon={IconUserPlus} title="No patients yet" compact action={<Link href="/reception/register" style={{ fontWeight: 600, color: 'var(--accent)' }}>Register the first patient</Link>}>
          Patients you register appear here.
        </EmptyState>
      ) : (
        <Stack gap={2}>
          {widget.data.map((p) => (
            <ListRow
              key={p.id}
              href={patientHref(role, p.id)}
              leading={
                <Avatar size={32} radius="xl" styles={{ placeholder: { background: 'var(--surface-2)', color: 'var(--text)', fontSize: 11, fontWeight: 600 } }}>
                  {initials(p.fullName)}
                </Avatar>
              }
              title={p.fullName}
              subtitle={`${p.patientCode} · ${ageGender(p.age, p.gender)} · ${formatRelative(p.registeredAt)}`}
              right={p.hasLogin ? <StatusBadge status="verified" label="Linked" /> : <StatusBadge status="pending" label="No login yet" />}
            />
          ))}
        </Stack>
      )}
    </Panel>
  );
}

function AccessLog({ widget }) {
  return (
    <Panel title={widget.title} subtitle="Who opened which patient record" right={<Link href="/admin/access-log" style={{ fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>Full log</Link>}>
      {widget.data.length === 0 ? (
        <EmptyState icon={IconFileSearch} title="No record opens yet" compact>
          Each time a doctor opens a patient&apos;s record, it&apos;s logged here.
        </EmptyState>
      ) : (
        <Stack gap={0}>
          {widget.data.map((row) => (
            <Group key={row.accessedAt + row.patientId} wrap="nowrap" justify="space-between" py={10} style={{ borderTop: '1px solid var(--border)' }}>
              <Box style={{ minWidth: 0 }}>
                <Text size="sm" truncate>
                  <Text span fw={600}>{row.userName}</Text> opened <Text span fw={600}>{row.patientName}</Text>
                </Text>
                <Text size="xs" c="var(--text-muted)">
                  {row.patientCode} · {formatDateTime(row.accessedAt)}
                </Text>
              </Box>
              <RoleBadge role={row.userRole} size="xs" />
            </Group>
          ))}
        </Stack>
      )}
    </Panel>
  );
}

function Team({ widget }) {
  const entries = Object.entries(widget.data);
  const max = Math.max(1, ...entries.map(([, n]) => n));
  return (
    <Panel title={widget.title} subtitle="Active accounts">
      <Stack gap="md">
        {entries.map(([role, count]) => (
          <div key={role}>
            <Group justify="space-between" mb={6}>
              <Text size="sm" fw={500}>{roleConfig(role)?.label ?? role}</Text>
              <Text size="sm" fw={600} className="mono">{count}</Text>
            </Group>
            <Progress value={(count / max) * 100} color="dark" size="sm" radius="xl" />
          </div>
        ))}
      </Stack>
    </Panel>
  );
}

function MyRecord({ widget }) {
  const r = widget.data;
  return (
    <Panel title={widget.title} right={<Link href="/patient/profile" style={{ fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>Open</Link>}>
      <Stack gap="md">
        <Group gap="lg">
          <div>
            <Text size="xs" c="var(--text-muted)">Patient ID</Text>
            <Text fw={600} className="mono">{r.patientCode}</Text>
          </div>
          <div>
            <Text size="xs" c="var(--text-muted)">Blood group</Text>
            <Group gap={4}>
              {r.bloodGroup && <IconDropletFilled size={14} color="var(--accent)" />}
              <Text fw={600} className="mono">{r.bloodGroup || '—'}</Text>
            </Group>
          </div>
        </Group>
        {r.knownAllergies ? (
          <SafetyBanner title="Allergies:">{r.knownAllergies}</SafetyBanner>
        ) : (
          <Text size="sm" c="var(--text-muted)">No allergies recorded.</Text>
        )}
        <Text size="xs" c="var(--text-subtle)">Last updated {formatRelative(r.updatedAt)}</Text>
      </Stack>
    </Panel>
  );
}

function UpcomingAppointments({ widget }) {
  return (
    <Panel title={widget.title}>
      {widget.data.length === 0 ? (
        <EmptyState
          icon={IconCalendarEvent}
          title="No upcoming appointments"
          compact
          action={<Link href="/patient/appointments/book" style={{ fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>Book an appointment</Link>}
        >
          Book a visit online, or ask the front desk.
        </EmptyState>
      ) : (
        <Stack gap={2}>
          {widget.data.map((a) => (
            <ListRow
              key={a.appointmentId}
              title={a.doctorName}
              subtitle={`${a.specialization} · ${formatDateTime(a.scheduledAt)}`}
              right={<StatusBadge status={APPOINTMENT_STATUS[a.status]} />}
            />
          ))}
        </Stack>
      )}
    </Panel>
  );
}

function Upcoming({ widget }) {
  return (
    <Panel title={widget.title} subtitle="These parts of your workspace switch on as the system is rolled out">
      <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="md">
        {widget.data.map((m) => (
          <Box key={m.module} p="md" style={{ borderRadius: 'var(--radius-md)', border: '1px dashed var(--border-strong)', background: 'var(--surface-alt)' }}>
            <Group justify="space-between" mb={6}>
              <Text fw={600} size="sm">{m.module}</Text>
              <Text size="xs" className="mono" c="var(--text-subtle)">Phase {String(m.phase).padStart(2, '0')}</Text>
            </Group>
            <Text size="sm" c="var(--text-muted)">{m.description}</Text>
          </Box>
        ))}
      </SimpleGrid>
    </Panel>
  );
}

const QUEUE_PAGE = { RECEPTIONIST: '/reception/queue', ADMIN: '/admin/queue' };

/** Today's queue; refreshed with the dashboard. Doctors call patients in and finish from here. */
function LiveQueue({ widget, role }) {
  const waiting = widget.data.doctors.reduce((n, c) => n + c.waiting.length, 0);
  const href = QUEUE_PAGE[role];
  return (
    <Panel
      title={widget.title}
      subtitle={`${waiting} waiting · updated ${formatRelative(widget.data.generatedAt)}`}
      right={href && <Link href={href} style={{ fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>Full board</Link>}
    >
      <QueueBoard board={widget.data} role={role} limit={4} />
    </Panel>
  );
}

const VISIT_STATUSES = [
  ['BOOKED', 'Booked', 'var(--info)'],
  ['CHECKED_IN', 'Waiting', 'var(--warning)'],
  ['IN_CONSULTATION', 'With doctor', 'var(--accent)'],
  ['COMPLETED', 'Done', 'var(--success)'],
  ['NO_SHOW', 'No-show', 'var(--text-subtle)'],
  ['CANCELLED', 'Cancelled', 'var(--border-strong)'],
];

/** Today's visits as one stacked bar plus a legend: where the day stands at a glance. */
function VisitsByStatus({ widget }) {
  const total = VISIT_STATUSES.reduce((n, [key]) => n + (widget.data[key] ?? 0), 0);
  return (
    <Panel title={widget.title} subtitle={`${total} visit${total === 1 ? '' : 's'} scheduled today`}>
      {total === 0 ? (
        <EmptyState icon={IconCalendarOff} title="No visits today" compact>Bookings and walk-ins show up here.</EmptyState>
      ) : (
        <Stack gap="md">
          <Group gap={3} wrap="nowrap" h={10} style={{ borderRadius: 999, overflow: 'hidden' }} aria-hidden>
            {VISIT_STATUSES.map(([key, , color]) =>
              widget.data[key] ? <Box key={key} h="100%" style={{ flex: widget.data[key], background: color }} /> : null,
            )}
          </Group>
          <Stack gap={8}>
            {VISIT_STATUSES.map(([key, label, color]) => (
              <Group key={key} justify="space-between">
                <Group gap={8}>
                  <Box w={8} h={8} style={{ borderRadius: 3, background: color }} />
                  <Text size="sm">{label}</Text>
                </Group>
                <Text size="sm" fw={600} className="mono">{widget.data[key] ?? 0}</Text>
              </Group>
            ))}
          </Stack>
        </Stack>
      )}
    </Panel>
  );
}

/** Patient's live spot in today's queue: their token, who is in now, how many are ahead. */
function MyQueue({ widget }) {
  const q = widget.data;
  const withDoctor = q.status === 'IN_CONSULTATION';
  return (
    <Box p="lg" style={{ borderRadius: 'var(--radius-lg)', background: 'var(--ink)', color: 'var(--on-ink)', position: 'relative', overflow: 'hidden' }}>
      <Group justify="space-between" align="center" wrap="wrap" gap="lg" style={{ position: 'relative', zIndex: 1 }}>
        <div>
          <Text size="xs" fw={600} tt="uppercase" style={{ letterSpacing: '0.08em', opacity: 0.65 }}>
            {withDoctor ? "It's your turn" : widget.title}
          </Text>
          <Text fz={52} fw={600} className="mono" lh={1.1}>{q.token}</Text>
          <Text size="sm" style={{ opacity: 0.8 }}>{q.doctorName} · {q.specialization}</Text>
        </div>
        <Group gap={40}>
          <div>
            <Text size="xs" style={{ opacity: 0.65 }}>Now with the doctor</Text>
            <Text fz={28} fw={600} className="mono">{q.nowServing || '—'}</Text>
          </div>
          <div>
            <Text size="xs" style={{ opacity: 0.65 }}>Ahead of you</Text>
            <Text fz={28} fw={600} className="mono">{withDoctor ? 0 : q.ahead}</Text>
          </div>
        </Group>
      </Group>
    </Box>
  );
}

/** Widget type → component. Unknown types are skipped, so the backend can add widgets safely. */
export const WIDGETS = {
  stats: Stats,
  schedule: Schedule,
  recentRecords: RecentRecords,
  recentPatients: RecentPatients,
  accessLog: AccessLog,
  team: Team,
  myRecord: MyRecord,
  upcomingAppointments: UpcomingAppointments,
  upcoming: Upcoming,
  liveQueue: LiveQueue,
  visitsByStatus: VisitsByStatus,
  myQueue: MyQueue,
  openConsultation: OpenConsultation,
  recentPrescriptions: RecentPrescriptions,
};
