'use client';

import { Alert, Button, Divider, Group, Stack, Text, Textarea } from '@mantine/core';
import { IconAlertCircle, IconCalendarCheck } from '@tabler/icons-react';
import { useState } from 'react';
import { clinicDate, dayLabel, useBookAppointment, useDoctors } from '@/lib/appointments';
import { friendlyMessage } from '@/lib/errors';
import { formatTime } from '@/lib/format';
import { DoctorPicker, PatientPicker, SlotPicker } from './Pickers';

function Step({ n, title, children, muted }) {
  return (
    <Stack gap="sm" style={{ opacity: muted ? 0.45 : 1, transition: 'opacity 200ms' }}>
      <Group gap={10}>
        <Text span className="mono" size="xs" fw={600} c="var(--text-subtle)">{String(n).padStart(2, '0')}</Text>
        <Text fw={600}>{title}</Text>
      </Group>
      {children}
    </Stack>
  );
}

/**
 * Book a slot: (patient, for the front desk) → doctor → day and time → reason. Used by the patient
 * booking page and the front-desk booking modal.
 */
export function BookingForm({ role, initialPatient = null, onBooked }) {
  const forFrontDesk = role === 'RECEPTIONIST';
  const doctors = useDoctors();
  const book = useBookAppointment();
  const [patient, setPatient] = useState(initialPatient);
  const [doctorId, setDoctorId] = useState(null);
  const [date, setDate] = useState(clinicDate());
  const [slot, setSlot] = useState(null);
  const [reason, setReason] = useState('');

  const doctor = doctors.data?.find((d) => d.id === doctorId);
  const ready = (!forFrontDesk || patient) && doctorId && slot;
  let n = 0;

  const submit = () =>
    book.mutate(
      { doctorId, scheduledAt: slot, patientId: forFrontDesk ? patient.id : undefined, reason: reason.trim() || undefined },
      {
        onSuccess: onBooked,
        // Someone took the slot meanwhile: clear it so the refreshed grid shows it as taken.
        onError: (error) => error.code === 'SLOT_TAKEN' && setSlot(null),
      },
    );

  return (
    <Stack gap="xl">
      {forFrontDesk && (
        <Step n={++n} title="Patient">
          <PatientPicker value={patient} onChange={setPatient} autoFocus={!initialPatient} />
        </Step>
      )}
      <Step n={++n} title="Doctor" muted={forFrontDesk && !patient}>
        <DoctorPicker value={doctorId} onChange={(id) => { setDoctorId(id); setSlot(null); }} requireHours />
      </Step>
      <Step n={++n} title="Day and time" muted={!doctorId}>
        {doctorId ? (
          <SlotPicker doctorId={doctorId} date={date} onDateChange={setDate} value={slot} onChange={setSlot} />
        ) : (
          <Text size="sm" c="var(--text-muted)">Choose a doctor to see free times.</Text>
        )}
      </Step>
      <Step n={++n} title="Reason for visit" muted={!slot}>
        <Textarea placeholder="Optional — e.g. fever for 3 days, follow-up on reports" maxLength={500} autosize minRows={2} value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
      </Step>

      {book.isError && (
        <Alert color="red" variant="light" radius="md" icon={<IconAlertCircle size={18} />}>
          {friendlyMessage(book.error)}
        </Alert>
      )}

      <Divider />
      <Group justify="space-between" wrap="wrap" gap="md">
        <Text size="sm" c={ready ? 'var(--text)' : 'var(--text-muted)'}>
          {ready ? (
            <>
              {forFrontDesk && <><b>{patient.fullName}</b> with </>}
              <b>{doctor?.fullName}</b> · {dayLabel(date, { weekday: 'long', day: 'numeric', month: 'short' })} at <b>{formatTime(slot)}</b>
            </>
          ) : (
            'Pick a doctor and a time to continue.'
          )}
        </Text>
        <Button size="md" leftSection={<IconCalendarCheck size={18} />} disabled={!ready} loading={book.isPending} onClick={submit}>
          Confirm booking
        </Button>
      </Group>
    </Stack>
  );
}
