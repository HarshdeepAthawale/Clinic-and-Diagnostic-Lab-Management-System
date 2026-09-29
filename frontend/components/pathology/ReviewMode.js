'use client';

import { Alert, Anchor, Badge, Box, Button, Group, Kbd, Modal, Progress, Radio, Skeleton, Stack, Text, Textarea } from '@mantine/core';
import { useHotkeys } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconAlertOctagon, IconArrowBackUp, IconArrowLeft, IconCheck, IconRefresh, IconShieldCheck } from '@tabler/icons-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import {
  RETURN_REASONS,
  formatValue,
  trendSeries,
  usePendingVerification,
  useReturnForRetest,
  useSampleResults,
  useVerify,
} from '@/lib/results';
import { friendlyMessage } from '@/lib/errors';
import { ageGender, formatDateTime } from '@/lib/format';
import { GlowCard } from '@/components/ui/GlowCard';
import { Sparkline } from '@/components/ui/Sparkline';
import { TubeChip } from '@/components/ui/TubeChip';
import { AttemptHistory } from '@/components/results/TestingPanels';
import { FlagBadge, ValueRange, ValueText, groupValues } from '@/components/results/ResultBits';

/** One parameter, large: the value, where it sits in its range, the patient's earlier values. */
function ParameterCard({ value, trend }) {
  const numeric = value.valueType === 'NUMERIC';
  const series = numeric && trend?.length ? trendSeries(trend, value.numericValue) : null;
  return (
    <Group wrap="nowrap" align="center" gap="lg" py="md" style={{ borderTop: '1px solid var(--border)' }}>
      <div style={{ flex: '1 1 0', minWidth: 0 }}>
        <Text fw={500}>{value.name}</Text>
        <Group gap={8} mt={4}>
          <FlagBadge flag={value.flag} />
        </Group>
      </div>
      <div style={{ textAlign: 'right', minWidth: 130 }}>
        <ValueText value={value} size="xl" />
        <Box mt={8} style={{ display: 'flex', justifyContent: 'flex-end' }}><ValueRange value={value} width={200} /></Box>
      </div>
      <Box visibleFrom="sm" w={130}>
        {series && series.length > 1 ? (
          <div>
            <Sparkline values={series} width={120} height={36} color={value.flag && value.flag !== 'NORMAL' ? 'var(--warning)' : 'var(--accent)'} label={`Trend for ${value.name}`} />
            <Text size="xs" c="var(--text-subtle)" className="mono">
              {trend.slice().reverse().map((t) => formatValue({ valueType: 'NUMERIC', numericValue: t.value })).join(' → ')} → now
            </Text>
          </div>
        ) : numeric ? (
          <Text size="xs" c="var(--text-subtle)">No earlier values</Text>
        ) : null}
      </Box>
    </Group>
  );
}

/**
 * Focus mode (Design.md §5.2): one result fills the screen. V verifies (with a confirm), R returns it
 * for a retest, J / K move through the queue, Esc goes back. Critical values get a red banner first.
 */
export function ReviewMode({ id }) {
  const router = useRouter();
  const query = useSampleResults(id);
  const queue = usePendingVerification(0, 100);
  const verify = useVerify(id);
  const back = useReturnForRetest(id);
  const [confirming, setConfirming] = useState(false);
  const [returning, setReturning] = useState(false);
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [tried, setTried] = useState(false);

  const list = queue.data?.content ?? [];
  const position = list.findIndex((r) => r.sampleId === id);
  const results = query.data;
  const pending = results?.status === 'RESULT_ENTERED';
  const busy = confirming || returning;

  const open = (sampleId) => router.push(`/pathology/review/${sampleId}`);
  const step = (delta) => {
    const target = list[position + delta];
    if (target) open(target.sampleId);
  };
  // After a decision, move to the next result in the queue, or back to the queue when it is empty.
  const next = () => {
    const target = list[position + 1] ?? list[position - 1];
    if (target && target.sampleId !== id) open(target.sampleId);
    else router.push('/pathology/queue');
  };

  useHotkeys([
    ['v', () => pending && !busy && setConfirming(true)],
    ['r', () => pending && !busy && setReturning(true)],
    ['j', () => !busy && step(1)],
    ['k', () => !busy && step(-1)],
    ['escape', () => !busy && router.push('/pathology/queue')],
  ], []);

  if (query.isPending) return <Stack gap="lg"><Skeleton height={48} radius="md" /><Skeleton height={420} radius="xl" /></Stack>;
  if (query.isError) {
    return (
      <Alert color="red" variant="light" radius="lg" icon={<IconAlertCircle size={18} />} title="Couldn’t open this result">
        {friendlyMessage(query.error)}
      </Alert>
    );
  }

  const latest = results.attempts[results.attempts.length - 1];
  const groups = latest ? groupValues(results.sheet, latest.values) : [];

  const doVerify = () =>
    verify.mutate(undefined, {
      onSuccess: (done) => {
        setConfirming(false);
        notifications.show({ title: 'Verified', message: `${done.sampleCode} signed off — the report has been created.`, color: 'teal', radius: 'lg', icon: <IconCheck size={18} /> });
        next();
      },
    });

  const doReturn = () => {
    setTried(true);
    if (!reason || (reason === 'OTHER' && !note.trim())) return;
    back.mutate(
      { reason, note: note.trim() || null },
      {
        onSuccess: (done) => {
          setReturning(false);
          setReason('');
          setNote('');
          setTried(false);
          notifications.show({ title: 'Returned for retest', message: `${done.sampleCode} is back on the lab’s testing list with your reason.`, color: 'yellow', radius: 'lg', icon: <IconArrowBackUp size={18} /> });
          next();
        },
      },
    );
  };

  return (
    <Stack gap="lg">
      <Group justify="space-between" wrap="wrap" gap="sm">
        <Anchor component={Link} href="/pathology/queue" size="sm" c="var(--text-muted)" fw={500} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <IconArrowLeft size={15} /> Queue <Kbd size="xs">Esc</Kbd>
        </Anchor>
        {position >= 0 && (
          <Group gap="sm" w={{ base: '100%', sm: 260 }} wrap="nowrap">
            <Text size="sm" c="var(--text-muted)" style={{ whiteSpace: 'nowrap' }}>{position + 1} of {list.length}</Text>
            <Progress value={((position + 1) / list.length) * 100} color="dark" size="sm" radius="xl" style={{ flex: 1 }} />
          </Group>
        )}
      </Group>

      {latest?.critical && pending && (
        <Alert color="red" variant="filled" radius="md" icon={<IconAlertOctagon size={20} />} title="Critical value">
          This result has a value at a critical limit. Check it carefully — consider a retest before signing off.
        </Alert>
      )}

      <GlowCard p="xl">
        <Group justify="space-between" align="flex-start" wrap="wrap" gap="md" mb="lg">
          <div>
            <Text fz={26} fw={600} lh={1.15} style={{ letterSpacing: '-0.02em' }}>{results.patient.fullName}</Text>
            <Text c="var(--text-muted)" className="mono" size="sm" mt={4}>
              {results.patient.patientCode} · {ageGender(results.patient.age, results.patient.gender)}
            </Text>
          </div>
          <Stack gap={4} align="flex-end">
            <Group gap={8}>
              <TubeChip tube={results.tubeType} />
              <Text size="sm" className="mono" fw={600}>{results.sampleCode}</Text>
            </Group>
            <Text size="xs" c="var(--text-muted)">
              Collected {results.collectedAt ? formatDateTime(results.collectedAt) : '—'}
              {results.receivedAt ? ` · received ${formatDateTime(results.receivedAt)}` : ''}
            </Text>
            {latest && (
              <Group gap={6}>
                {latest.attemptNumber > 1 && (
                  <Badge size="sm" radius="sm" leftSection={<IconRefresh size={12} />} styles={{ root: { textTransform: 'none', background: 'var(--warning-soft)', color: 'var(--warning)', fontWeight: 700 } }}>
                    Retest {latest.attemptNumber - 1}
                  </Badge>
                )}
                <Text size="xs" c="var(--text-muted)">Entered by {latest.enteredBy}{latest.analyzer ? ` on ${latest.analyzer}` : ''}</Text>
              </Group>
            )}
          </Stack>
        </Group>

        {!pending ? (
          <Alert color="blue" variant="light" radius="md" icon={<IconShieldCheck size={18} />}
            title={results.status === 'IN_TESTING' ? 'Back with the lab' : 'Nothing to sign off here'}>
            {results.status === 'IN_TESTING'
              ? 'This result was returned for a retest and is being retested.'
              : 'This sample has no result waiting for verification.'}{' '}
            <Anchor component={Link} href="/pathology/queue">Back to the queue</Anchor>
          </Alert>
        ) : (
          <Stack gap="xl">
            {groups.map((g) => (
              <Box key={g.key}>
                <Group gap={8}>
                  <Text fw={600} size="lg">{g.title}</Text>
                  <Text size="xs" className="mono" c="var(--text-subtle)">{g.code}</Text>
                </Group>
                {g.values.map((v) => <ParameterCard key={v.parameterId} value={v} trend={results.trend?.[v.parameterId]} />)}
              </Box>
            ))}
          </Stack>
        )}
      </GlowCard>

      {pending && (
        <GlowCard p="md" style={{ position: 'sticky', bottom: 16, zIndex: 5 }}>
          <Group justify="space-between" wrap="wrap" gap="sm">
            <Text size="sm" c="var(--text-muted)" visibleFrom="sm">
              <Kbd size="xs">V</Kbd> verify · <Kbd size="xs">R</Kbd> return for retest · <Kbd size="xs">J</Kbd> / <Kbd size="xs">K</Kbd> next / previous
            </Text>
            <Group gap="sm">
              <Button size="md" variant="default" color="yellow" leftSection={<IconArrowBackUp size={16} />} onClick={() => setReturning(true)}>
                Return for retest
              </Button>
              <Button size="md" leftSection={<IconCheck size={16} />} onClick={() => setConfirming(true)}>
                Verify and create report
              </Button>
            </Group>
          </Group>
        </GlowCard>
      )}

      {results.attempts.length > 1 && <AttemptHistory results={results} defaultOpen={false} />}

      <Modal opened={confirming} onClose={() => setConfirming(false)} title={<Text fw={600}>Verify this result?</Text>} size="md">
        <Stack gap="md">
          <Text size="sm">
            You are signing off <b>{results.patient.fullName}</b>’s results for <b>{results.sampleCode}</b>. A report is created with your name,
            qualification and registration number. This can’t be undone.
          </Text>
          {latest?.critical && <Alert color="red" variant="light" radius="md">This result includes a critical value.</Alert>}
          {verify.error && <Alert color="red" variant="light" radius="md">{friendlyMessage(verify.error)}</Alert>}
          <Group justify="flex-end" gap="sm">
            <Button variant="default" onClick={() => setConfirming(false)}>Not yet</Button>
            <Button onClick={doVerify} loading={verify.isPending} leftSection={<IconCheck size={16} />}>Verify</Button>
          </Group>
        </Stack>
      </Modal>

      <Modal opened={returning} onClose={() => setReturning(false)} title={<Text fw={600}>Return for retest</Text>} size="md">
        <Stack gap="md">
          <Radio.Group value={reason} onChange={setReason} label="Why?" error={tried && !reason ? 'Choose a reason' : null}>
            <Stack gap="xs" mt={8}>
              {RETURN_REASONS.map((r) => <Radio key={r.value} value={r.value} label={r.label} description={r.hint} />)}
            </Stack>
          </Radio.Group>
          <Textarea
            label={reason === 'OTHER' ? 'Note (required)' : 'Note for the lab (optional)'}
            autosize
            minRows={2}
            maxLength={500}
            value={note}
            onChange={(e) => setNote(e.currentTarget.value)}
            error={tried && reason === 'OTHER' && !note.trim() ? 'Add a note explaining why' : null}
          />
          <Text size="xs" c="var(--text-muted)">The same sample is retested. This attempt stays on record and the retest is checked separately.</Text>
          {back.error && <Alert color="red" variant="light" radius="md">{friendlyMessage(back.error)}</Alert>}
          <Group justify="flex-end" gap="sm">
            <Button variant="default" onClick={() => setReturning(false)}>Cancel</Button>
            <Button color="yellow" onClick={doReturn} loading={back.isPending} leftSection={<IconArrowBackUp size={16} />}>Return to the lab</Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
