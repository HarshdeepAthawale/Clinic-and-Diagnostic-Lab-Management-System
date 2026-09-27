'use client';

import { Autocomplete, Group, Text } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconPill } from '@tabler/icons-react';
import { useFormulary } from '@/lib/consultations';

/**
 * Medicine name with suggestions from the clinic formulary. Choosing a suggestion also reports its
 * usual strength so the dose can be pre-filled. Any name can still be typed.
 */
export function MedicineAutocomplete({ value, onChange, onPick, error, autoFocus }) {
  const [query] = useDebouncedValue(value, 200);
  const formulary = useFormulary(query);
  const items = formulary.data ?? [];
  const byValue = new Map(items.map((m) => [`${m.name} (${m.form})`, m]));

  return (
    <Autocomplete
      value={value}
      onChange={(next) => {
        const picked = byValue.get(next);
        if (picked) {
          onChange(picked.name);
          onPick?.(picked);
        } else {
          onChange(next);
        }
      }}
      data={[...byValue.keys()]}
      filter={({ options }) => options}
      placeholder="Medicine"
      error={error}
      autoFocus={autoFocus}
      leftSection={<IconPill size={15} stroke={1.6} />}
      renderOption={({ option }) => {
        const m = byValue.get(option.value);
        return (
          <Group gap={6} wrap="nowrap">
            <Text size="sm" fw={600}>{m?.name}</Text>
            <Text size="xs" c="var(--text-muted)">
              {m?.form}{m?.defaultStrength ? ` · ${m.defaultStrength}` : ''}
            </Text>
          </Group>
        );
      }}
      aria-label="Medicine"
      comboboxProps={{ withinPortal: true }}
    />
  );
}
