'use client';

import { Avatar, Button, Group, Stack, Text, Tooltip } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconArmchair, IconPlayerPlay, IconTicket } from '@tabler/icons-react';
import { AnimatePresence, motion, useReducedMotion } from 'motion/react';
import { useEffect, useState } from 'react';
import { minutesWaiting, useChangeStatus } from '@/lib/appointments';
import { friendlyMessage } from '@/lib/errors';
import { ageGender, formatTime } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { initials } from '@/components/shell/UserMenu';
import { AppointmentActions } from './AppointmentActions';
import classes from './QueueBoard.module.css';

/** Re-render every 30 s so waiting times stay honest between data refreshes. */
function useNow() {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, []);
  return now;
}

const LONG_WAIT_MINUTES = 30;

function Serving({ appointment, role, now, tv }) {
  const change = useChangeStatus();
  const minutes = minutesWaiting(appointment.startedAt, now);
  return (
    <div className={classes.serving}>
      <div className={classes.servingLabel}>
        <span className={classes.liveDot} aria-hidden />
        Now with the doctor
      </div>
      <div className={classes.servingMeta}>
        <div style={{ minWidth: 0 }}>
          <div className={classes.bigToken}>{appointment.queueToken}</div>
          {!tv && (
            <Text size="sm" c="rgb(255 255 255 / 0.75)" truncate>
              {appointment.patient.fullName} · {minutes} min
            </Text>
          )}
        </div>
        {role === 'DOCTOR' && (
          <Button
            size="xs"
            radius="md"
            variant="white"
            color="dark"
            loading={change.isPending}
            onClick={() =>
              change.mutate(
                { id: appointment.id, status: 'COMPLETED' },
                { onError: (e) => notifications.show({ message: friendlyMessage(e), color: 'red', radius: 'lg' }) },
              )
            }
          >
            Finish
          </Button>
        )}
      </div>
    </div>
  );
}

function Idle({ role, next }) {
  const change = useChangeStatus();
  return (
    <div className={classes.idle}>
      <Group justify="space-between" wrap="nowrap" gap="sm">
        <Group gap={8} wrap="nowrap">
          <IconArmchair size={18} stroke={1.6} />
          <span>{next ? 'Room is free' : 'Nobody waiting'}</span>
        </Group>
        {role === 'DOCTOR' && next && (
          <Button
            size="xs"
            radius="md"
            color="dark"
            leftSection={<IconPlayerPlay size={14} />}
            loading={change.isPending}
            onClick={() =>
              change.mutate(
                { id: next.id, status: 'IN_CONSULTATION' },
                { onError: (e) => notifications.show({ message: friendlyMessage(e), color: 'red', radius: 'lg' }) },
              )
            }
          >
            Call {next.queueToken}
          </Button>
        )}
      </Group>
    </div>
  );
}

function Column({ column, role, now, tv, limit }) {
  const reduceMotion = useReducedMotion();
  const waiting = limit ? column.waiting.slice(0, limit) : column.waiting;
  const hidden = column.waiting.length - waiting.length;
  return (
    <div className={classes.column}>
      <div className={classes.doctorRow}>
        <Avatar size={34} radius="xl" styles={{ placeholder: { background: 'var(--surface-2)', color: 'var(--text)', fontSize: 12, fontWeight: 600 } }}>
          {initials(column.doctor.fullName)}
        </Avatar>
        <div style={{ minWidth: 0 }}>
          <div className={classes.doctorName}>{column.doctor.fullName}</div>
          <Text size="xs" c="var(--text-muted)" truncate>{column.doctor.specialization}</Text>
        </div>
        {!tv && (
          <Tooltip label="Seen today · no-shows" withArrow>
            <span className={classes.tally}>{column.seen} seen · {column.noShows} no-show</span>
          </Tooltip>
        )}
      </div>

      {column.nowServing ? (
        <Serving appointment={column.nowServing} role={role} now={now} tv={tv} />
      ) : (
        <Idle role={role} next={column.waiting[0]} />
      )}

      <div className={classes.waitingHead}>
        <span>Waiting</span>
        <span className="mono">{column.waiting.length}</span>
      </div>

      <Stack gap={4}>
        <AnimatePresence initial={false}>
          {waiting.map((a, index) => {
            const minutes = minutesWaiting(a.checkedInAt, now);
            return (
              <motion.div
                key={a.id}
                layout={!reduceMotion}
                initial={reduceMotion ? false : { opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={reduceMotion ? { opacity: 0 } : { opacity: 0, x: 24 }}
                transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
                className={`${classes.row} ${index === 0 ? classes.next : ''}`}
              >
                <span className={classes.chip}>{a.queueToken}</span>
                {!tv && (
                  <div className={classes.who}>
                    <Text size="sm" fw={600} truncate>{a.patient.fullName}</Text>
                    <Text size="xs" c="var(--text-muted)" truncate>
                      {ageGender(a.patient.age, a.patient.gender)} · {a.kind === 'WALK_IN' ? 'walk-in' : `booked ${formatTime(a.scheduledAt)}`}
                    </Text>
                  </div>
                )}
                <span className={`${classes.wait} ${minutes >= LONG_WAIT_MINUTES ? classes.long : ''}`}>{minutes} min</span>
                {!tv && role !== 'ADMIN' && <AppointmentActions role={role} appointment={a} compact />}
              </motion.div>
            );
          })}
        </AnimatePresence>
        {hidden > 0 && <Text size="xs" c="var(--text-subtle)" px={4}>+{hidden} more waiting</Text>}
        {column.waiting.length === 0 && <Text size="xs" c="var(--text-subtle)" px={4}>No one in line.</Text>}
      </Stack>
    </div>
  );
}

/**
 * Today's queue, one column per doctor. Doctors call patients in and finish from here; the front
 * desk handles no-shows and cancellations; admin watches. `tv` shows tokens only, for a waiting-room
 * screen, so no patient names are ever displayed publicly.
 */
export function QueueBoard({ board, role, tv = false, limit }) {
  const now = useNow();
  if (!board.doctors.length) {
    return (
      <EmptyState icon={IconTicket} title="The queue is empty" compact={!tv}>
        Patients appear here the moment they are checked in or given a walk-in token.
      </EmptyState>
    );
  }
  return (
    <div className={classes.grid}>
      {board.doctors.map((column) => (
        <Column key={column.doctor.id} column={column} role={role} now={now} tv={tv} limit={limit} />
      ))}
    </div>
  );
}

export { classes as queueClasses };
