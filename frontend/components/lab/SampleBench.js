'use client';

import { Group, Loader, Pagination, SegmentedControl, Skeleton, Stack, Text, TextInput } from '@mantine/core';
import { IconCircleCheck, IconQrcode } from '@tabler/icons-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { findSampleByCode, normalizeSampleCode, useWaitingSamples } from '@/lib/samples';
import { friendlyMessage } from '@/lib/errors';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { SampleRow } from './SampleBits';

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

export function SampleBenchView() {
  const [step, setStep] = useState('ORDERED');
  const [page, setPage] = useState(1);
  const list = useWaitingSamples(step, page - 1);
  const totalPages = list.data ? Math.ceil(list.data.totalElements / list.data.size) : 0;

  return (
    <Stack gap="xl">
      <PageTitle
        title="Samples"
        subtitle="Scan a label to open a sample, or pick the next one from the list. Urgent orders and redraws come first."
      />
      <ScanField />
      <GlowCard p="lg">
        <Group justify="space-between" mb="md" wrap="wrap" gap="sm">
          <SegmentedControl
            size="md"
            value={step}
            onChange={(v) => { setStep(v); setPage(1); }}
            data={[
              { label: `To collect${step === 'ORDERED' && list.data ? ` · ${list.data.totalElements}` : ''}`, value: 'ORDERED' },
              { label: `To receive${step === 'COLLECTED' && list.data ? ` · ${list.data.totalElements}` : ''}`, value: 'COLLECTED' },
            ]}
          />
          {list.data && (
            <Group gap={8}>
              <span className="live-dot" />
              <Text size="sm" c="var(--text-muted)">Updates automatically</Text>
            </Group>
          )}
        </Group>
        {list.isPending ? (
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
        {totalPages > 1 && (
          <Group justify="center" mt="md">
            <Pagination total={totalPages} value={page} onChange={setPage} size="sm" color="dark" />
          </Group>
        )}
      </GlowCard>
    </Stack>
  );
}
