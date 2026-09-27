import { Box, Group, Stack, Text } from '@mantine/core';
import { IconSalad } from '@tabler/icons-react';
import { prepItems } from '@/lib/lab';

/**
 * What the patient must do before their sample is taken (fasting, first-morning urine…), shown
 * as a checklist so it can't be missed. Renders nothing when no test needs preparation.
 */
export function PrepChecklist({ items, title = 'Before your test' }) {
  const prep = prepItems(items);
  if (prep.length === 0) return null;
  return (
    <Box p="md" style={{ borderRadius: 'var(--radius-md)', background: 'var(--warning-soft)', border: '1px solid color-mix(in oklab, var(--warning) 22%, transparent)' }}>
      <Group gap={8} mb={8}>
        <IconSalad size={18} color="var(--warning)" />
        <Text fw={700} size="sm" c="var(--warning)">{title}</Text>
      </Group>
      <Stack gap={6} component="ul" m={0} p={0} style={{ listStyle: 'none' }}>
        {prep.map((p) => (
          <Group key={p.name} component="li" gap={10} wrap="nowrap" align="flex-start">
            <Box mt={7} w={6} h={6} style={{ flex: 'none', borderRadius: 999, background: 'var(--warning)' }} />
            <Text size="sm">
              <Text span fw={600}>{p.name}</Text> — {p.prep}
            </Text>
          </Group>
        ))}
      </Stack>
    </Box>
  );
}
