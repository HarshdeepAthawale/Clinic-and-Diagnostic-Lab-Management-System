'use client';

import { ActionIcon, Autocomplete, Box, Button, Grid, Group, Stack, Text, TextInput, Tooltip } from '@mantine/core';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import { AnimatePresence, motion } from 'motion/react';
import { MedicineAutocomplete } from './MedicineAutocomplete';
import { DURATIONS, EMPTY_MEDICINE, FREQUENCIES, INSTRUCTIONS, isPartialMedicine } from './presets';

const MAX_MEDICINES = 30;

/**
 * Keyboard-friendly prescription rows (Design.md §5.5): medicine → dose → frequency → duration →
 * instructions. Rows animate in and out; a row started but left incomplete is flagged.
 */
export function PrescriptionBuilder({ form, disabled = false }) {
  const medicines = form.values.medicines;

  const add = () => form.insertListItem('medicines', { ...EMPTY_MEDICINE });

  return (
    <Stack gap="sm">
      {medicines.length === 0 && (
        <Text size="sm" c="var(--text-subtle)">
          No medicines yet. A prescription is issued only if you add at least one.
        </Text>
      )}

      <AnimatePresence initial={false}>
        {medicines.map((m, i) => {
          const partial = isPartialMedicine(m);
          return (
            <motion.div
              key={i}
              layout
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
            >
              <Box
                p="sm"
                style={{
                  borderRadius: 'var(--radius-md)',
                  border: `1px solid ${partial ? 'color-mix(in oklab, var(--warning) 45%, transparent)' : 'var(--border)'}`,
                  background: 'var(--surface-alt)',
                }}
              >
                <Grid gutter="xs" align="flex-start">
                  <Grid.Col span={{ base: 12, md: 4 }}>
                    <Group gap={8} wrap="nowrap" align="flex-start">
                      <Text className="mono" size="sm" c="var(--text-subtle)" pt={8} w={16}>{i + 1}</Text>
                      <div style={{ flex: 1 }}>
                        <MedicineAutocomplete
                          value={m.medicine}
                          onChange={(v) => form.setFieldValue(`medicines.${i}.medicine`, v)}
                          onPick={(picked) => !m.dosage && picked.defaultStrength && form.setFieldValue(`medicines.${i}.dosage`, picked.defaultStrength)}
                          autoFocus={i === medicines.length - 1 && !m.medicine}
                        />
                      </div>
                    </Group>
                  </Grid.Col>
                  <Grid.Col span={{ base: 6, md: 2 }}>
                    <TextInput placeholder="Dose" aria-label="Dose" disabled={disabled} {...form.getInputProps(`medicines.${i}.dosage`)} />
                  </Grid.Col>
                  <Grid.Col span={{ base: 6, md: 2 }}>
                    <Autocomplete
                      placeholder="Frequency"
                      aria-label="Frequency"
                      data={FREQUENCIES}
                      styles={{ input: { fontFamily: 'var(--font-mono)' } }}
                      {...form.getInputProps(`medicines.${i}.frequency`)}
                    />
                  </Grid.Col>
                  <Grid.Col span={{ base: 6, md: 2 }}>
                    <Autocomplete placeholder="Duration" aria-label="Duration" data={DURATIONS} {...form.getInputProps(`medicines.${i}.duration`)} />
                  </Grid.Col>
                  <Grid.Col span={{ base: 6, md: 2 }}>
                    <Group gap={4} wrap="nowrap">
                      <Autocomplete placeholder="Instructions" aria-label="Instructions" data={INSTRUCTIONS} style={{ flex: 1 }} {...form.getInputProps(`medicines.${i}.instructions`)} />
                      <Tooltip label="Remove">
                        <ActionIcon variant="subtle" color="gray" onClick={() => form.removeListItem('medicines', i)} aria-label={`Remove medicine ${i + 1}`}>
                          <IconTrash size={16} />
                        </ActionIcon>
                      </Tooltip>
                    </Group>
                  </Grid.Col>
                </Grid>
                {partial && (
                  <Text size="xs" c="var(--warning)" fw={600} mt={6} ml={24}>
                    Add a frequency and duration, or remove this row.
                  </Text>
                )}
              </Box>
            </motion.div>
          );
        })}
      </AnimatePresence>

      <Group>
        <Button variant="default" leftSection={<IconPlus size={16} />} onClick={add} disabled={disabled || medicines.length >= MAX_MEDICINES}>
          Add medicine
        </Button>
      </Group>
    </Stack>
  );
}
