'use client';

import { Badge, Group, Skeleton, Stack, Text } from '@mantine/core';
import { IconStethoscope } from '@tabler/icons-react';
import { usePatientConsultations } from '@/lib/consultations';
import { formatDate } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { ListRow } from '@/components/dashboard/widgets/ListRow';

/**
 * A patient's completed visits, newest first. `hrefFor(visit)` decides where a row links (the
 * doctor's consultation page, or the patient's prescription); `excludeId` hides the current visit.
 */
export function VisitHistory({ patientId, hrefFor, excludeId, limit, compact = false }) {
  const visits = usePatientConsultations(patientId);
  if (visits.isPending) return <Stack gap="xs">{[0, 1].map((i) => <Skeleton key={i} height={44} radius="md" />)}</Stack>;
  if (visits.isError) return <Text size="sm" c="var(--text-muted)">Visit history isn&apos;t available.</Text>;

  const rows = visits.data.filter((v) => v.id !== excludeId).slice(0, limit ?? undefined);
  if (rows.length === 0) {
    return (
      <EmptyState icon={IconStethoscope} title="No previous visits" compact={compact}>
        Completed consultations appear here.
      </EmptyState>
    );
  }
  return (
    <Stack gap={2}>
      {rows.map((v) => (
        <ListRow
          key={v.id}
          href={hrefFor?.(v)}
          title={v.diagnosis}
          subtitle={`${formatDate(v.visitAt)} · ${v.doctorName}`}
          right={
            v.prescriptionCode ? (
              <Group gap={4} wrap="nowrap">
                <Badge size="sm" radius="sm" variant="outline" color="dark" styles={{ root: { textTransform: 'none', fontFamily: 'var(--font-mono)' } }}>
                  {v.prescriptionCode}
                </Badge>
              </Group>
            ) : null
          }
        />
      ))}
    </Stack>
  );
}
