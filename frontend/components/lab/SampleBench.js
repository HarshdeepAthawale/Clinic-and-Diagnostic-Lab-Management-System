'use client';

import { Group, Loader, Pagination, SegmentedControl, Skeleton, Stack, Text, TextInput } from '@mantine/core';
import { IconCircleCheck, IconQrcode } from '@tabler/icons-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { RETURN_REASON_LABEL, useToTest } from '@/lib/results';
import { findSampleByCode, normalizeSampleCode, useWaitingSamples } from '@/lib/samples';
import { friendlyMessage } from '@/lib/errors';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { RetestBadge, SampleRow, TestingRow } from './SampleBits';

/**
 * Scan-first (Design.md §5.3): a big always-focused field takes a typed code or a barcode scanner's
 * keystrokes and jumps straight to that sample's next action. Below it, what is waiting.
 */
function ScanField() {
  const router = useRouter();
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const go = async (event) => {
    event.preventDefault();
    const code = normalizeSampleCode(value);
    if (!code) {
      setError('That doesn’t look like a sample code — they read LAB-20260929-0007.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const sample = await findSampleByCode(code);
      router.push(`/lab/samples/${sample.id}`);
    } catch (e) {
      setError(e?.status === 404 ? `No sample with the code ${code}.` : friendlyMessage(e));
      setBusy(false);
    }
  };

  return (
    <form onSubmit={go}>
      <TextInput
        size="xl"
        radius="lg"
        autoFocus
        data-autofocus
        placeholder="Scan or type the sample code"
        leftSection={<IconQrcode size={24} stroke={1.6} />}
        rightSection={busy ? <Loader size="sm" /> : null}
        value={value}
        onChange={(e) => { setValue(e.currentTarget.value); setError(null); }}
        error={error}
        aria-label="Scan or type the sample code"
        styles={{ input: { fontFamily: 'var(--font-mono)', height: 60 } }}
      />
    </form>
  );
}

/** The "to test" list: accepted samples and ones back from the pathologist, returned first with the reason. */
function ToTestList({ page, onPage }) {
  const list = useToTest(page - 1);
  const totalPages = list.data ? Math.ceil(list.data.totalElements / list.data.size) : 0;
  return (
    <>
      {list.isPending ? (
        <Stack gap="xs">{[0, 1, 2].map((i) => <Skeleton key={i} height={56} radius="md" />)}</Stack>
      ) : list.isError ? (
        <Text c="var(--critical)" size="sm">{friendlyMessage(list.error)}</Text>
      ) : list.data.content.length === 0 ? (
        <EmptyState icon={IconCircleCheck} title="Nothing to test">
          Samples that pass the receipt check appear here, ready to test.
        </EmptyState>
      ) : (
        <Stack gap={2}>
          {list.data.content.map((s) => <TestingRow key={s.id} row={s} href={`/lab/samples/${s.id}`} reasonLabel={RETURN_REASON_LABEL} />)}
        </Stack>
      )}
      {totalPages > 1 && (
        <Group justify="center" mt="md">
          <Pagination total={totalPages} value={page} onChange={onPage} size="sm" color="dark" />
        </Group>
      )}
    </>
  );
}

export function SampleBenchView() {
  const [step, setStep] = useState('ORDERED');
  const [page, setPage] = useState(1);
  const list = useWaitingSamples(step, page - 1, 20, step !== 'TESTING');
  const totalPages = list.data ? Math.ceil(list.data.totalElements / list.data.size) : 0;
  const count = (status) => (step === status && list.data ? ` · ${list.data.totalElements}` : '');

  return (
    <Stack gap="xl">
      <PageTitle
        title="Samples"
        subtitle="Scan a label to open a sample, or pick the next one from the list. Urgent orders, redraws and retests come first."
      />
      <ScanField />
      <GlowCard p="lg">
        <Group justify="space-between" mb="md" wrap="wrap" gap="sm">
          <SegmentedControl
            size="md"
            value={step}
            onChange={(v) => { setStep(v); setPage(1); }}
            data={[
              { label: `To collect${count('ORDERED')}`, value: 'ORDERED' },
              { label: `To receive${count('COLLECTED')}`, value: 'COLLECTED' },
              { label: 'To test', value: 'TESTING' },
            ]}
          />
          <Group gap={8}>
            <span className="live-dot" />
            <Text size="sm" c="var(--text-muted)">Updates automatically</Text>
          </Group>
        </Group>
        {step === 'TESTING' ? (
          <ToTestList page={page} onPage={setPage} />
        ) : list.isPending ? (
          <Stack gap="xs">{[0, 1, 2].map((i) => <Skeleton key={i} height={56} radius="md" />)}</Stack>
        ) : list.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(list.error)}</Text>
        ) : list.data.content.length === 0 ? (
          <EmptyState icon={IconCircleCheck} title={step === 'ORDERED' ? 'Nothing to collect' : 'Nothing waiting to be received'}>
            {step === 'ORDERED' ? 'Samples appear here the moment a doctor orders tests.' : 'Collected samples appear here until they pass the receipt check.'}
          </EmptyState>
        ) : (
          <Stack gap={2}>
            {list.data.content.map((s) => <SampleRow key={s.id} sample={s} href={`/lab/samples/${s.id}`} />)}
          </Stack>
        )}
        {step !== 'TESTING' && totalPages > 1 && (
          <Group justify="center" mt="md">
            <Pagination total={totalPages} value={page} onChange={setPage} size="sm" color="dark" />
          </Group>
        )}
      </GlowCard>
    </Stack>
  );
}
