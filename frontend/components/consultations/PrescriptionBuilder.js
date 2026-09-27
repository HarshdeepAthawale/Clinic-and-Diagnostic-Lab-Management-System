'use client';

import { ActionIcon, Alert, Autocomplete, Button, Group, NumberInput, Stack, Text, Textarea, TextInput } from '@mantine/core';
import { IconAlertCircle, IconPlus, IconPrescription, IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { FREQUENCIES, INSTRUCTIONS, useMedicineSuggestions } from '@/lib/consultations';
import { friendlyMessage } from '@/lib/errors';

const emptyRow = () => ({ key: Math.random().toString(36).slice(2), medicine: '', dose: '', frequency: '', durationDays: '', instructions: '' });

/** Medicine field: suggests what this clinic has prescribed before and fills in its usual dose/frequency. */
function MedicineInput({ row, onChange, autoFocus }) {
  const suggestions = useMedicineSuggestions(row.medicine);
  const options = suggestions.data ?? [];
  return (
    <Autocomplete
      placeholder="Medicine and strength"
      value={row.medicine}
      onChange={(medicine) => onChange({ medicine })}
      onOptionSubmit={(value) => {
        const s = options.find((o) => o.medicine === value);
        if (!s) return;
        onChange({
          medicine: s.medicine,
          dose: row.dose || s.dose || '',
          frequency: row.frequency || s.frequency || '',
          durationDays: row.durationDays || s.durationDays || '',
        });
      }}
      data={options.map((o) => o.medicine)}
      renderOption={({ option }) => {
        const s = options.find((o) => o.medicine === option.value);
        return (
          <div>
            <Text size="sm" fw={600}>{s.medicine}</Text>
            <Text size="xs" c="var(--text-muted)">
              {[s.dose, s.frequency, s.durationDays && `${s.durationDays} days`].filter(Boolean).join(' · ')} · prescribed {s.timesPrescribed}×
            </Text>
          </div>
        );
      }}
      filter={({ options: all }) => all}
      autoFocus={autoFocus}
      radius="md"
      aria-label="Medicine"
      style={{ flex: 3, minWidth: 180 }}
    />
  );
}

/**
 * Prescription builder: one row per medicine (medicine → dose → when → days → instructions), fast to
 * fill from the keyboard. With `reviseOf`, it asks why and issues a corrected prescription that
 * replaces the old one — issued prescriptions are never edited.
 */
export function PrescriptionBuilder({ mutation, reviseOf = null, initialAdvice = '', onDone, onCancel }) {
  const [rows, setRows] = useState(() =>
    reviseOf
      ? reviseOf.items.map((i) => ({ ...emptyRow(), ...i, dose: i.dose ?? '', durationDays: i.durationDays ?? '', instructions: i.instructions ?? '' }))
      : [emptyRow()],
  );
  const [advice, setAdvice] = useState(reviseOf?.advice ?? initialAdvice);
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);

  const setRow = (key, patch) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  const addRow = () => setRows((rs) => [...rs, emptyRow()]);
  const removeRow = (key) => setRows((rs) => (rs.length === 1 ? [emptyRow()] : rs.filter((r) => r.key !== key)));

  const filled = rows.filter((r) => r.medicine.trim());
  const invalid = filled.some((r) => !r.frequency.trim());
  const canIssue = filled.length > 0 && !invalid && (!reviseOf || reason.trim());

  const submit = () => {
    setTouched(true);
    if (!canIssue) return;
    mutation.mutate(
      {
        reviseId: reviseOf?.id,
        reason: reviseOf ? reason.trim() : undefined,
        advice: advice.trim() || undefined,
        items: filled.map((r) => ({
          medicine: r.medicine.trim(),
          dose: r.dose.trim() || undefined,
          frequency: r.frequency.trim(),
          durationDays: r.durationDays === '' ? undefined : Number(r.durationDays),
          instructions: r.instructions.trim() || undefined,
        })),
      },
      { onSuccess: onDone },
    );
  };

  return (
    <Stack gap="md">
      <Stack gap={8}>
        {rows.map((row, index) => (
          <Group key={row.key} gap={8} wrap="wrap" align="flex-start">
            <Text className="mono" size="xs" c="var(--text-subtle)" w={18} mt={10}>{index + 1}</Text>
            <MedicineInput row={row} onChange={(patch) => setRow(row.key, patch)} autoFocus={index > 0 && index === rows.length - 1 && !row.medicine} />
            <TextInput placeholder="Dose" value={row.dose} onChange={(e) => setRow(row.key, { dose: e.currentTarget.value })} radius="md" style={{ flex: 1.3, minWidth: 100 }} aria-label="Dose" />
            <Autocomplete
              placeholder="When"
              data={FREQUENCIES}
              value={row.frequency}
              onChange={(frequency) => setRow(row.key, { frequency })}
              error={touched && row.medicine.trim() && !row.frequency.trim() ? 'Required' : undefined}
              radius="md"
              style={{ flex: 1.2, minWidth: 100 }}
              aria-label="Frequency"
            />
            <NumberInput placeholder="Days" min={1} max={365} value={row.durationDays} onChange={(v) => setRow(row.key, { durationDays: v })} radius="md" w={84} aria-label="Days" hideControls />
            <Autocomplete
              placeholder="Instructions"
              data={INSTRUCTIONS}
              value={row.instructions}
              onChange={(instructions) => setRow(row.key, { instructions })}
              onKeyDown={(e) => e.key === 'Enter' && index === rows.length - 1 && row.medicine.trim() && addRow()}
              radius="md"
              style={{ flex: 1.6, minWidth: 130 }}
              aria-label="Instructions"
            />
            <ActionIcon variant="subtle" color="gray" mt={4} onClick={() => removeRow(row.key)} aria-label="Remove medicine">
              <IconTrash size={16} />
            </ActionIcon>
          </Group>
        ))}
        <Group>
          <Button variant="subtle" color="dark" size="compact-sm" leftSection={<IconPlus size={14} />} onClick={addRow}>Add medicine</Button>
          <Text size="xs" c="var(--text-subtle)">Tip: press Enter in the last box to add another row.</Text>
        </Group>
      </Stack>

      <Textarea label="Advice" placeholder="Diet, rest, when to come back…" autosize minRows={2} maxLength={4000} value={advice} onChange={(e) => setAdvice(e.currentTarget.value)} />
      {reviseOf && (
        <TextInput
          label="Why is this being revised?"
          placeholder="e.g. Wrong strength, allergy reported"
          required
          maxLength={300}
          value={reason}
          onChange={(e) => setReason(e.currentTarget.value)}
          error={touched && !reason.trim() ? 'Required — the original stays on record with this reason' : undefined}
        />
      )}

      {mutation.isError && (
        <Alert color="red" variant="light" radius="md" icon={<IconAlertCircle size={18} />}>{friendlyMessage(mutation.error)}</Alert>
      )}
      <Group justify="space-between">
        <Text size="xs" c="var(--text-muted)">
          {reviseOf ? `Issues a new prescription that replaces ${reviseOf.code}.` : 'Once issued, a prescription can only be revised, never edited.'}
        </Text>
        <Group gap="sm">
          {onCancel && <Button variant="default" onClick={onCancel}>Cancel</Button>}
          <Button leftSection={<IconPrescription size={16} />} loading={mutation.isPending} disabled={filled.length === 0} onClick={submit}>
            {reviseOf ? 'Issue revised prescription' : `Issue prescription (${filled.length})`}
          </Button>
        </Group>
      </Group>
    </Stack>
  );
}
