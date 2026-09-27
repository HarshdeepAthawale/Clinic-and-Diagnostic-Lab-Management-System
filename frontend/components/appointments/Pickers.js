'use client';

import { Avatar, Group, Loader, Select, Skeleton, Stack, Text, UnstyledButton } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconCalendarOff, IconSearch } from '@tabler/icons-react';
import { useState } from 'react';
import { addDays, clinicDate, dayLabel, useDoctors, useSlots } from '@/lib/appointments';
import { ageGender, CLINIC_TIME_ZONE, formatTime } from '@/lib/format';
import { usePatientSearch } from '@/lib/patients';
import { EmptyState } from '@/components/ui/EmptyState';
import { initials } from '@/components/shell/UserMenu';
import classes from './Pickers.module.css';

/** Search-as-you-type patient chooser for the front desk. Calls `onChange(summary)`. */
export function PatientPicker({ value, onChange, autoFocus }) {
  const [query, setQuery] = useState('');
  const [debounced] = useDebouncedValue(query.trim(), 250);
  const search = usePatientSearch(debounced, 0, 8);
  const results = search.data?.content ?? [];
  const options = [...(value && !results.some((p) => p.id === value.id) ? [value] : []), ...results];

  return (
    <Select
      label="Patient"
      placeholder="Name, phone or patient ID"
      searchable
      searchValue={query}
      onSearchChange={setQuery}
      filter={({ options: all }) => all}
      data={options.map((p) => ({ value: p.id, label: `${p.fullName} · ${p.patientCode}` }))}
      value={value?.id ?? null}
      onChange={(id) => onChange(options.find((p) => p.id === id) ?? null)}
      renderOption={({ option }) => {
        const p = options.find((x) => x.id === option.value);
        return (
          <div>
            <Text size="sm" fw={600}>{p.fullName}</Text>
            <Text size="xs" c="var(--text-muted)">{p.patientCode} · {ageGender(p.age, p.gender)} · {p.maskedPhone}</Text>
          </div>
        );
      }}
      leftSection={<IconSearch size={16} />}
      rightSection={search.isFetching ? <Loader size="xs" /> : undefined}
      nothingFoundMessage={debounced ? 'No patients match. Register them first.' : 'Start typing to search'}
      autoFocus={autoFocus}
      radius="md"
      comboboxProps={{ radius: 'md', shadow: 'md' }}
    />
  );
}

/** Doctor cards. Doctors without working hours can still take walk-ins but not bookings. */
export function DoctorPicker({ value, onChange, requireHours = false }) {
  const doctors = useDoctors();
  if (doctors.isPending) return <Skeleton height={64} radius="md" />;
  if (!doctors.data?.length) {
    return <Text size="sm" c="var(--text-muted)">No doctors have been added yet.</Text>;
  }
  return (
    <div className={classes.doctors} role="radiogroup" aria-label="Doctor">
      {doctors.data.map((d) => {
        const disabled = requireHours && !d.hasWorkingHours;
        return (
          <UnstyledButton
            key={d.id}
            role="radio"
            aria-checked={value === d.id}
            disabled={disabled}
            className={`${classes.doctor} ${value === d.id ? classes.selected : ''}`}
            onClick={() => onChange(d.id)}
          >
            <Avatar size={36} radius="xl" styles={{ placeholder: { background: 'var(--surface-2)', color: 'var(--text)', fontSize: 12, fontWeight: 600 } }}>
              {initials(d.fullName)}
            </Avatar>
            <div style={{ minWidth: 0 }}>
              <Text size="sm" fw={600} truncate>{d.fullName}</Text>
              <Text size="xs" c="var(--text-muted)" truncate>{disabled ? 'No booking hours set' : d.specialization}</Text>
            </div>
          </UnstyledButton>
        );
      })}
    </div>
  );
}

const DAYS_SHOWN = 14;

/** Two-week date strip, then that day's slots split into morning / afternoon / evening. */
export function SlotPicker({ doctorId, date, onDateChange, value, onChange }) {
  const slots = useSlots(doctorId, date);
  const today = clinicDate();
  const days = Array.from({ length: DAYS_SHOWN }, (_, i) => addDays(today, i));

  const periods = [
    ['Morning', (h) => h < 12],
    ['Afternoon', (h) => h >= 12 && h < 17],
    ['Evening', (h) => h >= 17],
  ];
  const hourOf = (iso) => Number(new Intl.DateTimeFormat('en-GB', { hour: 'numeric', hour12: false, timeZone: CLINIC_TIME_ZONE }).format(new Date(iso)));
  const free = slots.data?.slots.filter((s) => s.available).length ?? 0;

  return (
    <Stack gap="md">
      <div className={classes.days} role="radiogroup" aria-label="Date">
        {days.map((d) => (
          <UnstyledButton
            key={d}
            role="radio"
            aria-checked={d === date}
            className={`${classes.day} ${d === date ? classes.dayActive : ''}`}
            onClick={() => {
              onDateChange(d);
              onChange(null);
            }}
          >
            <div className={classes.dayName}>{d === today ? 'Today' : dayLabel(d, { weekday: 'short' })}</div>
            <div className={classes.dayNum}>{Number(d.slice(8))}</div>
            <div className={classes.dayName}>{dayLabel(d, { month: 'short' })}</div>
          </UnstyledButton>
        ))}
      </div>

      {slots.isPending ? (
        <Skeleton height={120} radius="md" />
      ) : !slots.data?.working ? (
        <EmptyState icon={IconCalendarOff} title="Not seeing patients this day" compact>
          Pick another date.
        </EmptyState>
      ) : free === 0 ? (
        <EmptyState icon={IconCalendarOff} title="Fully booked" compact>
          Every slot on {dayLabel(date)} is taken or has passed. Try another day.
        </EmptyState>
      ) : (
        <Stack gap="sm">
          <Group justify="space-between">
            <Text size="sm" fw={600}>{dayLabel(date, { weekday: 'long', day: 'numeric', month: 'long' })}</Text>
            <Text size="xs" c="var(--text-muted)">{free} free · {slots.data.slotMinutes}-minute slots</Text>
          </Group>
          {periods.map(([name, match]) => {
            const inPeriod = slots.data.slots.filter((s) => match(hourOf(s.startsAt)));
            if (!inPeriod.length) return null;
            return (
              <div key={name}>
                <div className={classes.period}>{name}</div>
                <div className={classes.slots}>
                  {inPeriod.map((s) => (
                    <button
                      type="button"
                      key={s.startsAt}
                      disabled={!s.available}
                      className={`${classes.slot} ${value === s.startsAt ? classes.slotActive : ''}`}
                      aria-pressed={value === s.startsAt}
                      onClick={() => onChange(s.startsAt)}
                    >
                      {formatTime(s.startsAt)}
                    </button>
                  ))}
                </div>
              </div>
            );
          })}
        </Stack>
      )}
    </Stack>
  );
}
