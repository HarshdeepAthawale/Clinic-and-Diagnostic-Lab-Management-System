'use client';

import { Accordion, Alert, Badge, Button, Group, Modal, Radio, Skeleton, Stack, Text, Textarea } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconAlertOctagon, IconArrowBackUp, IconCircleCheck, IconFlask, IconPlayerPlay, IconX } from '@tabler/icons-react';
import Link from 'next/link';
import { useState } from 'react';
import {
  RETURN_REASON_LABEL,
  TESTING_REJECT_REASONS,
  useRejectInTesting,
  useSampleResults,
  useStartTesting,
} from '@/lib/results';
import { friendlyMessage } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import { Panel } from '@/components/ui/Panel';
import { ResultEntry } from './ResultEntry';
import { groupValues, ResultTable } from './ResultBits';

/** Testing begins on an accepted sample. */
function StartTesting({ results }) {
  const start = useStartTesting(results.sampleId);
  return (
    <Panel title="Ready to test" subtitle="This sample passed the receipt check">
      <Stack gap="md">
        {start.error && <Alert color="red" variant="light" radius="md">{friendlyMessage(start.error)}</Alert>}
        <Button size="lg" onClick={() => start.mutate()} loading={start.isPending} leftSection={<IconPlayerPlay size={18} />}>
          Start testing
        </Button>
      </Stack>
    </Panel>
  );
}

/**
 * Reject a sample that is in testing — used up (often after a retest) or degraded. It stays on record,
 * the front desk is told to call the patient back, and a redraw is created; results already entered are kept.
 */
export function RejectInTesting({ results }) {
  const reject = useRejectInTesting(results.sampleId);
  const [opened, setOpened] = useState(false);
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [tried, setTried] = useState(false);
  const noteMissing = reason === 'OTHER' && !note.trim();

  const submit = () => {
    setTried(true);
    if (!reason || noteMissing) return;
    reject.mutate(
      { reason, note: note.trim() || null },
      {
        onSuccess: () => {
          setOpened(false);
          notifications.show({
            title: `${results.sampleCode} rejected`,
            message: `The front desk has been told to call ${results.patient.fullName} back for a new sample.`,
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
    <>
      <Button variant="subtle" color="red" onClick={() => setOpened(true)} leftSection={<IconX size={16} />}>
        Sample used up or unusable? Reject it
      </Button>
      <Modal opened={opened} onClose={() => setOpened(false)} title={<Text fw={600}>Reject {results.sampleCode} during testing?</Text>} size="md">
        <Stack gap="md">
          <Radio.Group value={reason} onChange={setReason} label="Why?" error={tried && !reason ? 'Choose a reason' : null}>
            <Stack gap="xs" mt={8}>
              {TESTING_REJECT_REASONS.map((r) => <Radio key={r.value} value={r.value} label={r.label} description={r.hint} />)}
            </Stack>
          </Radio.Group>
          <Textarea
            label={reason === 'OTHER' ? 'Note (required)' : 'Note (optional)'}
            autosize
            minRows={2}
            maxLength={500}
            value={note}
            onChange={(e) => setNote(e.currentTarget.value)}
            error={tried && noteMissing ? 'Add a note explaining the rejection' : null}
          />
          <Alert color="red" variant="light" radius="md">
            The sample and any results already entered stay on record. The front desk is told to call {results.patient.fullName} back, and a new sample is created.
          </Alert>
          {reject.error && <Alert color="red" variant="light" radius="md">{friendlyMessage(reject.error)}</Alert>}
          <Group justify="flex-end" gap="sm">
            <Button variant="default" onClick={() => setOpened(false)}>Keep it</Button>
            <Button color="red" onClick={submit} loading={reject.isPending} leftSection={<IconX size={16} />}>Reject and notify front desk</Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

function AttemptBadge({ attempt }) {
  if (attempt.status === 'VERIFIED') return <Badge size="sm" radius="sm" color="teal" variant="light" styles={{ root: { textTransform: 'none' } }}>Verified</Badge>;
  if (attempt.status === 'RETURNED_FOR_RETEST') return <Badge size="sm" radius="sm" color="yellow" variant="light" styles={{ root: { textTransform: 'none' } }}>Returned</Badge>;
  return <Badge size="sm" radius="sm" color="gray" variant="light" styles={{ root: { textTransform: 'none' } }}>Awaiting verification</Badge>;
}

/** Every attempt so far, newest first, each expandable to its values — a retest never hides the first result. */
export function AttemptHistory({ results, defaultOpen }) {
  if (results.attempts.length === 0) return null;
  const attempts = [...results.attempts].reverse();
  return (
    <Panel title="Result history" subtitle={results.retestCount ? `${results.retestCount} retest${results.retestCount === 1 ? '' : 's'}` : 'All attempts are kept'}>
      <Accordion variant="separated" radius="md" multiple defaultValue={defaultOpen ? [attempts[0].id] : []}>
        {attempts.map((a) => (
          <Accordion.Item key={a.id} value={a.id}>
            <Accordion.Control>
              <Group gap="sm" justify="space-between" pr="sm" wrap="nowrap">
                <Group gap={8}>
                  <Text fw={600} size="sm">Attempt {a.attemptNumber}</Text>
                  <AttemptBadge attempt={a} />
                  {a.critical && <Badge size="sm" radius="sm" color="red" variant="light" styles={{ root: { textTransform: 'none' } }}>Critical</Badge>}
                </Group>
                <Text size="xs" c="var(--text-muted)">{formatDateTime(a.enteredAt)}</Text>
              </Group>
            </Accordion.Control>
            <Accordion.Panel>
              <Stack gap="sm">
                <Text size="xs" c="var(--text-muted)">
                  Entered by {a.enteredBy}{a.analyzer ? ` on ${a.analyzer}` : ''}
                  {a.verifiedBy && ` · verified by ${a.verifiedBy} ${formatDateTime(a.verifiedAt)}`}
                  {a.returnedBy && ` · returned by ${a.returnedBy} ${formatDateTime(a.returnedAt)}`}
                </Text>
                {a.returnReason && (
                  <Alert color="yellow" variant="light" radius="md" p="xs">
                    <Text size="sm" fw={600}>{RETURN_REASON_LABEL[a.returnReason] ?? a.returnReason}</Text>
                    {a.returnNote && <Text size="sm">{a.returnNote}</Text>}
                  </Alert>
                )}
                <ResultTable groups={groupValues(results.sheet, a.values)} compact />
              </Stack>
            </Accordion.Panel>
          </Accordion.Item>
        ))}
      </Accordion>
    </Panel>
  );
}

/** A sample entered and waiting: what the pathologist will see, plus a note that it is out of the bench's hands. */
function AwaitingVerification({ results }) {
  const latest = results.attempts[results.attempts.length - 1];
  return (
    <Stack gap="lg">
      <Panel title="With the pathologist" subtitle={`Attempt ${latest.attemptNumber} is waiting for verification`}>
        <Stack gap="md">
          {latest.critical && (
            <Alert color="red" variant="light" radius="md" icon={<IconAlertOctagon size={18} />} title="Critical value">
              The pathologist sees critical results first.
            </Alert>
          )}
          <ResultTable groups={groupValues(results.sheet, latest.values)} compact />
          <Text size="xs" c="var(--text-subtle)">If it comes back for a retest, it returns to your testing list with the reason.</Text>
        </Stack>
      </Panel>
    </Stack>
  );
}

function Verified({ results }) {
  return (
    <Panel title="Verified" subtitle="A pathologist has signed this result off">
      <Stack gap="sm">
        <Group gap={8}>
          <IconCircleCheck size={18} color="var(--success)" />
          <Text size="sm">The report has been created.</Text>
        </Group>
        <Button component={Link} href="/lab/reports" variant="default" leftSection={<IconFlask size={16} />} w="fit-content">
          Go to reports to send
        </Button>
      </Stack>
    </Panel>
  );
}

/**
 * The bench panel for a sample from "at the lab" onwards: start testing, enter results (with any
 * retest reason), wait for the pathologist, or see that it is done. The attempt history sits below.
 */
export function TestingStep({ sample }) {
  const query = useSampleResults(sample.id);
  if (query.isPending) return <Skeleton height={220} radius="xl" />;
  if (query.isError) {
    return (
      <Alert color="red" variant="light" radius="lg" icon={<IconAlertCircle size={18} />} title="Couldn’t load the results">
        {friendlyMessage(query.error)}
      </Alert>
    );
  }
  const results = query.data;
  return (
    <Stack gap="lg">
      {results.status === 'RECEIVED_AT_LAB' && <StartTesting results={results} />}
      {results.status === 'IN_TESTING' && (
        <>
          <ResultEntry key={results.attempts.length} results={results} />
          <Group justify="flex-end"><RejectInTesting results={results} /></Group>
        </>
      )}
      {results.status === 'RESULT_ENTERED' && <AwaitingVerification results={results} />}
      {['VERIFIED', 'REPORT_GENERATED', 'DISPATCHED'].includes(results.status) && <Verified results={results} />}
      <AttemptHistory results={results} defaultOpen={results.status !== 'IN_TESTING' && results.status !== 'RESULT_ENTERED'} />
    </Stack>
  );
}
