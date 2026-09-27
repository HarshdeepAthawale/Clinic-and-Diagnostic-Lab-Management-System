'use client';

import { NumberInput, SimpleGrid } from '@mantine/core';

const FIELDS = [
  { key: 'bpSystolic', label: 'BP systolic', suffix: 'mmHg', min: 40, max: 300 },
  { key: 'bpDiastolic', label: 'BP diastolic', suffix: 'mmHg', min: 20, max: 200 },
  { key: 'pulseBpm', label: 'Pulse', suffix: 'bpm', min: 20, max: 250 },
  { key: 'temperatureC', label: 'Temperature', suffix: '°C', min: 30, max: 45, decimals: 1 },
  { key: 'spo2Percent', label: 'SpO₂', suffix: '%', min: 50, max: 100 },
  { key: 'weightKg', label: 'Weight', suffix: 'kg', min: 0.5, max: 400, decimals: 1 },
];

/** Six optional vitals with the same limits the server enforces. */
export function VitalsInput({ form }) {
  return (
    <SimpleGrid cols={{ base: 2, sm: 3, lg: 6 }} spacing="sm">
      {FIELDS.map((f) => (
        <NumberInput
          key={f.key}
          label={f.label}
          rightSection={<span style={{ fontSize: 11, color: 'var(--text-subtle)', paddingRight: 6 }}>{f.suffix}</span>}
          rightSectionWidth={f.suffix.length > 2 ? 48 : 32}
          hideControls
          min={f.min}
          max={f.max}
          decimalScale={f.decimals ?? 0}
          allowNegative={false}
          styles={{ input: { fontFamily: 'var(--font-mono)' } }}
          {...form.getInputProps(`vitals.${f.key}`)}
        />
      ))}
    </SimpleGrid>
  );
}

/** "BP 124/82 · Pulse 88 · 38.4 °C · SpO₂ 98% · 64.5 kg", or null if nothing was recorded. */
export function vitalsSummary(v) {
  if (!v) return null;
  const parts = [];
  if (v.bpSystolic != null && v.bpDiastolic != null) parts.push(`BP ${v.bpSystolic}/${v.bpDiastolic}`);
  if (v.pulseBpm != null) parts.push(`Pulse ${v.pulseBpm}`);
  if (v.temperatureC != null) parts.push(`${v.temperatureC} °C`);
  if (v.spo2Percent != null) parts.push(`SpO₂ ${v.spo2Percent}%`);
  if (v.weightKg != null) parts.push(`${v.weightKg} kg`);
  return parts.length ? parts.join(' · ') : null;
}
