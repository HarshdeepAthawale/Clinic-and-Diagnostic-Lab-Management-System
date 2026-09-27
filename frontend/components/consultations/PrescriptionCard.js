'use client';

import { Badge, Box, Button, Group, Stack, Table, Text } from '@mantine/core';
import { IconEdit, IconFileTypePdf, IconRefresh } from '@tabler/icons-react';
import { describeFrequency, prescriptionPdfUrl } from '@/lib/consultations';
import { formatDateTime } from '@/lib/format';

/**
 * An issued prescription: code, doctor, medicine table, advice, PDF. A replaced one stays visible,
 * struck through, so the record shows what was actually issued.
 */
export function PrescriptionCard({ rx, onRevise, showPatient = false, compact = false }) {
  const replaced = Boolean(rx.supersededBy);
  return (
    <Box
      p={compact ? 'md' : 'lg'}
      style={{
        borderRadius: 'var(--radius-lg)',
        border: `1px solid ${replaced ? 'var(--border)' : 'var(--border-strong)'}`,
        background: replaced ? 'var(--surface-alt)' : 'var(--surface)',
        opacity: replaced ? 0.7 : 1,
      }}
    >
      <Group justify="space-between" align="flex-start" wrap="wrap" gap="sm" mb="md">
        <div>
          <Group gap={8}>
            <Text fw={700} fz={22} c="var(--accent)" lh={1} aria-hidden>℞</Text>
            <Text className="mono" fw={600} td={replaced ? 'line-through' : undefined}>{rx.code}</Text>
            {replaced && <Badge color="gray" variant="light" radius="sm">Replaced by {rx.supersededBy.code}</Badge>}
            {rx.replaces && <Badge color="dark" variant="outline" radius="sm" leftSection={<IconRefresh size={11} />}>Revises {rx.replaces.code}</Badge>}
          </Group>
          <Text size="xs" c="var(--text-muted)" mt={4}>
            {showPatient && `${rx.patient.fullName} · `}
            {rx.doctor.fullName} · {formatDateTime(rx.issuedAt)}
          </Text>
          {rx.diagnosis && <Text size="sm" mt={4}><Text span c="var(--text-muted)">Diagnosis:</Text> {rx.diagnosis}</Text>}
        </div>
        <Group gap={8}>
          {onRevise && !replaced && (
            <Button size="xs" variant="default" leftSection={<IconEdit size={14} />} onClick={() => onRevise(rx)}>Revise</Button>
          )}
          <Button size="xs" color="dark" component="a" href={prescriptionPdfUrl(rx.id)} target="_blank" rel="noopener" leftSection={<IconFileTypePdf size={14} />}>
            PDF
          </Button>
        </Group>
      </Group>

      <Table verticalSpacing={8} horizontalSpacing="sm" withRowBorders>
        <Table.Thead>
          <Table.Tr>
            {['Medicine', 'Dose', 'When', 'For'].map((h) => (
              <Table.Th key={h} fz={11} c="var(--text-muted)" tt="uppercase" style={{ letterSpacing: '0.05em' }}>{h}</Table.Th>
            ))}
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rx.items.map((item) => (
            <Table.Tr key={item.position}>
              <Table.Td>
                <Text size="sm" fw={600}>{item.medicine}</Text>
                {item.instructions && <Text size="xs" c="var(--text-muted)">{item.instructions}</Text>}
              </Table.Td>
              <Table.Td><Text size="sm">{item.dose ?? '—'}</Text></Table.Td>
              <Table.Td>
                <Text size="sm" className="mono">{item.frequency}</Text>
                {describeFrequency(item.frequency) && <Text size="xs" c="var(--text-muted)">{describeFrequency(item.frequency)}</Text>}
              </Table.Td>
              <Table.Td><Text size="sm" style={{ whiteSpace: 'nowrap' }}>{item.durationDays ? `${item.durationDays} days` : '—'}</Text></Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>

      {(rx.advice || rx.revisionReason) && (
        <Stack gap={4} mt="md">
          {rx.advice && <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}><Text span fw={600}>Advice: </Text>{rx.advice}</Text>}
          {rx.revisionReason && <Text size="xs" c="var(--text-muted)">Revised because: {rx.revisionReason}</Text>}
        </Stack>
      )}
    </Box>
  );
}
