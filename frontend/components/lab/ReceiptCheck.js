'use client';

import { Alert, Button, Group, Modal, Radio, Stack, Text, Textarea } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconArrowBackUp, IconCheck, IconX } from '@tabler/icons-react';
import { useState } from 'react';
import { RECEIPT_REJECT_REASONS, useReceiveSample } from '@/lib/samples';
import { friendlyMessage } from '@/lib/errors';
import { Panel } from '@/components/ui/Panel';
import { TUBES } from '@/components/ui/TubeChip';

/**
 * The receipt / quality check: one primary action, "Mark received". A tube mismatch shows as an
 * amber warning first. Rejecting needs a reason (and a note for "something else"), keeps the sample
 * as a permanent record, tells the front desk to call the patient back and creates the redraw.
 */
export function ReceiptCheck({ sample }) {
  const receive = useReceiveSample(sample.id);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [tried, setTried] = useState(false);

  const noteMissing = reason === 'OTHER' && !note.trim();

  const accept = () =>
    receive.mutate(
      { accepted: true },
      {
        onSuccess: (done) =>
          notifications.show({ title: `${done.sampleCode} received`, message: 'It has passed the quality check.', color: 'teal', radius: 'lg', icon: <IconCheck size={18} /> }),
      },
    );

  const reject = () => {
    setTried(true);
    if (!reason || noteMissing) return;
    receive.mutate(
      { accepted: false, reason, note: note.trim() || null },
      {
        onSuccess: (done) => {
          setRejecting(false);
          notifications.show({
            title: `${done.sampleCode} rejected`,
            message: `The front desk has been told to call ${done.patient.fullName} back. Redraw ${done.redrawSampleCode} is ready to collect.`,
            color: 'red',
            radius: 'lg',
            icon: <IconArrowBackUp size={18} />,
            autoClose: 8000,
          });
        },
      },
    );
  };

  return (
    <Panel title="Receipt check" subtitle="Is this sample fit to test?">
      <Stack gap="lg">
        {sample.tubeMismatch && (
          <Alert color="yellow" variant="light" radius="md" icon={<IconAlertTriangle size={18} />} title="Collected in the wrong tube">
            Drawn into {TUBES[sample.tubeTypeUsed]?.label ?? sample.tubeTypeUsed}, but these tests need{' '}
            {TUBES[sample.requiredTubeType]?.label ?? sample.requiredTubeType}. Check the sample carefully — reject it if the results
            could be affected.
          </Alert>
        )}
        {receive.error && !rejecting && (
          <Alert color="red" variant="light" radius="md">{friendlyMessage(receive.error)}</Alert>
        )}
        <Group gap="sm" grow>
          <Button size="lg" onClick={accept} loading={receive.isPending && !rejecting} leftSection={<IconCheck size={18} />}>
            Mark received
          </Button>
          <Button size="lg" variant="default" color="red" onClick={() => setRejecting(true)} leftSection={<IconX size={18} />}>
            Reject sample
          </Button>
        </Group>
      </Stack>

      <Modal opened={rejecting} onClose={() => setRejecting(false)} title={<Text fw={600}>Reject {sample.sampleCode}?</Text>} size="md">
        <Stack gap="md">
          <Radio.Group value={reason} onChange={setReason} label="What is wrong with it?" error={tried && !reason ? 'Choose a reason' : null}>
            <Stack gap="xs" mt={8}>
              {RECEIPT_REJECT_REASONS.map((r) => (
                <Radio key={r.value} value={r.value} label={r.label} description={r.hint} />
              ))}
            </Stack>
          </Radio.Group>
          <Textarea
            label={reason === 'OTHER' ? 'Note (required)' : 'Note (optional)'}
            placeholder="Anything the next person should know"
            autosize
            minRows={2}
            maxLength={500}
            value={note}
            onChange={(e) => setNote(e.currentTarget.value)}
            error={tried && noteMissing ? 'Add a note explaining the rejection' : null}
          />
          <Alert color="red" variant="light" radius="md">
            The sample stays on record. The front desk is told to call {sample.patient.fullName} back, and a new sample is created for a redraw.
          </Alert>
          {receive.error && rejecting && <Alert color="red" variant="light" radius="md">{friendlyMessage(receive.error)}</Alert>}
          <Group justify="flex-end" gap="sm">
            <Button variant="default" onClick={() => setRejecting(false)}>Keep it</Button>
            <Button color="red" onClick={reject} loading={receive.isPending} leftSection={<IconX size={16} />}>Reject and notify front desk</Button>
          </Group>
        </Stack>
      </Modal>
    </Panel>
  );
}
