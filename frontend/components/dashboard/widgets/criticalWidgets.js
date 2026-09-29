'use client';

import { Alert, Box, Button, Group, Modal, Stack, Text, Textarea, ThemeIcon } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconAlertOctagon, IconCheck } from '@tabler/icons-react';
import Link from 'next/link';
import { useState } from 'react';
import { isOverdue, parametersText, useAcknowledgeCritical, waitingFor } from '@/lib/critical';
import { friendlyMessage } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import { GlowCard } from '@/components/ui/GlowCard';

const REPORT_PAGE = { DOCTOR: '/doctor/reports', LAB_TECHNICIAN: '/lab/reports' };

/** The doctor confirms they have seen it; an optional note records what was done (phoned, sent to ED...). */
function AcknowledgeModal({ alert, onClose }) {
  const acknowledge = useAcknowledgeCritical();
  const [note, setNote] = useState('');
  const submit = (event) => {
    event.preventDefault();
    acknowledge.mutate(
      { sampleId: alert.sampleId, note },
      {
        onSuccess: () => {
          notifications.show({ title: 'Critical result acknowledged', message: alert.patientName, color: 'teal', radius: 'lg', icon: <IconCheck size={18} /> });
          onClose();
        },
      },
    );
  };
  return (
    <Modal opened onClose={onClose} title="Acknowledge critical result" radius="lg" centered>
      <form onSubmit={submit}>
        <Stack gap="sm">
          <div>
            <Text fw={600}>{alert.patientName}</Text>
            <Text size="sm" c="var(--critical)">{parametersText(alert.parameters)}</Text>
            <Text size="xs" c="var(--text-muted)">{alert.testNames.join(', ')} · verified {formatDateTime(alert.verifiedAt)}</Text>
          </div>
          <Text size="sm" c="var(--text-muted)">
            Acknowledging records that you have seen this result. It doesn&apos;t change the report or stop it being sent to the patient.
          </Text>
          <Textarea
            label="What was done (optional)"
            placeholder="Phoned the patient, asked them to come in today"
            maxLength={300}
            autosize
            minRows={2}
            data-autofocus
            value={note}
            onChange={(e) => setNote(e.currentTarget.value)}
          />
          {acknowledge.isError && (
            <Alert color="red" variant="light" icon={<IconAlertCircle size={16} />}>{friendlyMessage(acknowledge.error)}</Alert>
          )}
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>Not yet</Button>
            <Button type="submit" color="red" loading={acknowledge.isPending}>Acknowledge</Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}

function AlertRow({ alert, role, onAcknowledge }) {
  const overdue = isOverdue(alert.verifiedAt);
  const base = REPORT_PAGE[role];
  return (
    <Group justify="space-between" align="flex-start" wrap="wrap" gap="sm" py={12} style={{ borderTop: '1px solid var(--border)' }}>
      <div style={{ minWidth: 0, flex: '1 1 260px' }}>
        <Group gap={8} wrap="wrap">
          <Text fw={600}>{alert.patientName}</Text>
          <Text size="xs" c="var(--text-muted)" className="mono">{alert.patientCode}</Text>
          <Text size="xs" fw={600} c={overdue ? 'var(--critical)' : 'var(--text-muted)'}>
            waiting {waitingFor(alert.verifiedAt)}
          </Text>
        </Group>
        <Text size="sm" c="var(--critical)" fw={500}>{parametersText(alert.parameters)}</Text>
        <Text size="xs" c="var(--text-muted)">
          {alert.testNames.join(', ')}
          {role === 'LAB_TECHNICIAN' ? ` · ordered by ${alert.orderingDoctor}` : ''}
        </Text>
      </div>
      <Group gap="xs" wrap="nowrap">
        {base && (
          <Button component={Link} href={`${base}/${alert.sampleId}`} size="compact-sm" variant="default">Open report</Button>
        )}
        {role === 'DOCTOR' && (
          <Button size="compact-sm" color="red" onClick={() => onAcknowledge(alert)}>Acknowledge</Button>
        )}
      </Group>
    </Group>
  );
}

/**
 * Critical results waiting for acknowledgement, pinned above everything else. The doctor acknowledges their
 * own; the lab sees everyone's, in case it has to phone the doctor.
 */
export function CriticalResults({ widget, role }) {
  const { open, items } = widget.data;
  const [acknowledging, setAcknowledging] = useState(null);
  return (
    <GlowCard p="lg" style={{ borderColor: 'color-mix(in oklab, var(--critical) 45%, transparent)', background: 'var(--critical-soft)' }}>
      <Group gap="sm" mb={4} wrap="nowrap">
        <ThemeIcon size={34} radius="md" style={{ background: 'var(--critical)', color: 'white' }}>
          <IconAlertOctagon size={20} />
        </ThemeIcon>
        <div>
          <Text fw={700} c="var(--critical)">{widget.title} · {open}</Text>
          <Text size="xs" c="var(--text-muted)">
            {role === 'DOCTOR' ? 'A value is at a critical limit. Please review and acknowledge.' : 'Waiting for the ordering doctor to acknowledge.'}
          </Text>
        </div>
      </Group>
      <Box>
        {items.map((alert) => (
          <AlertRow key={alert.sampleId} alert={alert} role={role} onAcknowledge={setAcknowledging} />
        ))}
      </Box>
      {open > items.length && <Text size="xs" c="var(--text-muted)" mt="xs">And {open - items.length} more.</Text>}
      {acknowledging && <AcknowledgeModal alert={acknowledging} onClose={() => setAcknowledging(null)} />}
    </GlowCard>
  );
}

/** Admin: only how many are waiting and since when. No patients or tests. */
export function CriticalSummary({ widget }) {
  const { open, oldestVerifiedAt } = widget.data;
  const overdue = oldestVerifiedAt && isOverdue(oldestVerifiedAt);
  return (
    <GlowCard p="lg">
      <Group gap="sm" mb="sm" wrap="nowrap">
        <ThemeIcon size={30} radius="md" variant="light" style={{ background: 'var(--critical-soft)', color: 'var(--critical)' }}>
          <IconAlertOctagon size={18} />
        </ThemeIcon>
        <Text fw={600}>{widget.title}</Text>
      </Group>
      <Text fz={34} fw={700} lh={1.1} className="mono" c="var(--critical)">{open}</Text>
      <Text size="sm" c={overdue ? 'var(--critical)' : 'var(--text-muted)'} mt={4}>
        {oldestVerifiedAt ? `The longest has waited ${waitingFor(oldestVerifiedAt)}` : ''}
      </Text>
      <Text size="xs" c="var(--text-subtle)" mt={6}>Each is waiting for the doctor who ordered the test.</Text>
    </GlowCard>
  );
}
