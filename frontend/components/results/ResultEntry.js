'use client';

import { Alert, Box, Button, Group, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertOctagon, IconAlertTriangle, IconCheck, IconSend } from '@tabler/icons-react';
import { useMemo, useRef, useState } from 'react';
import { entryBody, entryProblems, entrySummary, flagFor, formatRange, formatValue, useEnterResults } from '@/lib/results';
import { RETURN_REASON_LABEL } from '@/lib/results';
import { friendlyMessage } from '@/lib/errors';
import { Panel } from '@/components/ui/Panel';
import { FlagBadge } from './ResultBits';
import { ValueRange } from './ResultBits';

const ANALYZER_KEY = 'cdlms.analyzer';

/** The analyzer used last time, remembered in this browser only (a per-person convenience, never required). */
function rememberedAnalyzer() {
  try {
    return window.localStorage.getItem(ANALYZER_KEY) ?? '';
  } catch {
    return '';
  }
}

function remember(analyzer) {
  try {
    window.localStorage.setItem(ANALYZER_KEY, analyzer);
  } catch {
    // Private mode or blocked storage: nothing to remember, nothing lost.
  }
}

/** The values of the latest returned attempt by parameter, so a retest can show what was read last time. */
function lastAttemptValues(results) {
  const returned = [...results.attempts].reverse().find((a) => a.status === 'RETURNED_FOR_RETEST');
  return new Map((returned?.values ?? []).map((v) => [v.parameterId, v]));
}

/**
 * Result entry (Design.md §5.3): one card per test with a big input per parameter. The flag shows as you
 * type (the server recomputes it), Enter moves to the next field, and a retest shows the returned
 * attempt's values beside the inputs. Every parameter needs a value before it can be submitted.
 */
export function ResultEntry({ results }) {
  const enter = useEnterResults(results.sampleId);
  const [values, setValues] = useState({});
  const [analyzer, setAnalyzer] = useState(rememberedAnalyzer);
  const [tried, setTried] = useState(false);
  const inputs = useRef([]);

  const previous = useMemo(() => lastAttemptValues(results), [results]);
  const problems = entryProblems(results.sheet, values);
  const { abnormal, critical } = entrySummary(results.sheet, values);
  const flat = results.sheet.flatMap((t) => t.parameters);

  const focusNext = (index) => {
    const next = inputs.current[index + 1];
    if (next) next.focus();
  };

  const submit = () => {
    setTried(true);
    if (Object.keys(problems).length > 0) {
      const first = flat.findIndex((p) => problems[p.parameterId]);
      inputs.current[first]?.focus();
      return;
    }
    remember(analyzer.trim());
    enter.mutate(entryBody(results.sheet, values, analyzer), {
      onSuccess: () =>
        notifications.show({
          title: `${results.sampleCode} sent for verification`,
          message: 'A pathologist will check it before a report is made.',
          color: 'teal',
          radius: 'lg',
          icon: <IconCheck size={18} />,
        }),
    });
  };

  let index = -1;
  return (
    <Stack gap="lg">
      {results.lastReturnReason && (
        <Alert color="yellow" variant="light" radius="md" icon={<IconAlertTriangle size={18} />}
          title={`Returned for retest · ${RETURN_REASON_LABEL[results.lastReturnReason] ?? results.lastReturnReason}`}>
          {results.lastReturnNote && <Text size="sm">{results.lastReturnNote}</Text>}
          <Text size="xs" c="var(--text-muted)">
            Attempt {results.attempts.length + 1}. The earlier values are shown beside each field; they stay on record.
          </Text>
        </Alert>
      )}

      <Panel title="Enter results" subtitle="A value for every parameter — the range check runs as you type">
        <Stack gap="lg">
          <TextInput
            label="Analyzer / machine"
            placeholder="e.g. Sysmex XN-1000"
            maxLength={60}
            value={analyzer}
            onChange={(e) => setAnalyzer(e.currentTarget.value)}
            w={{ base: '100%', sm: 320 }}
          />

          {results.sheet.map((test) => (
            <Box key={test.itemId}>
              <Group gap={8} mb={6}>
                <Text fw={600}>{test.testName}</Text>
                <Text size="xs" className="mono" c="var(--text-subtle)">{test.testCode}</Text>
              </Group>
              <Stack gap={0}>
                {test.parameters.map((p) => {
                  index += 1;
                  const i = index;
                  const raw = values[p.parameterId] ?? '';
                  const numeric = p.valueType === 'NUMERIC';
                  const flag = numeric ? flagFor(raw, p) : null;
                  const before = previous.get(p.parameterId);
                  return (
                    <Group key={p.parameterId} wrap="nowrap" align="center" gap="md" py={8} style={{ borderTop: '1px solid var(--border)' }}>
                      <div style={{ flex: '1 1 0', minWidth: 0 }}>
                        <Text size="sm" fw={500}>{p.name}</Text>
                        <Text size="xs" c="var(--text-subtle)">
                          {numeric ? `Reference ${formatRange(p)}${p.unit ? ` ${p.unit}` : ''}` : 'Enter as text, e.g. Negative'}
                          {before && ` · last time ${formatValue(before)}`}
                        </Text>
                      </div>
                      <TextInput
                        ref={(el) => { inputs.current[i] = el; }}
                        size="md"
                        w={{ base: 120, sm: 150 }}
                        inputMode={numeric ? 'decimal' : 'text'}
                        aria-label={`${test.testName} — ${p.name}`}
                        rightSection={p.unit ? <Text size="xs" c="var(--text-subtle)" pr={6}>{p.unit}</Text> : null}
                        rightSectionWidth={p.unit ? 54 : undefined}
                        value={raw}
                        maxLength={numeric ? 20 : 200}
                        error={tried ? problems[p.parameterId] : null}
                        onChange={(e) => setValues((v) => ({ ...v, [p.parameterId]: e.currentTarget.value }))}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            if (i === flat.length - 1) submit();
                            else focusNext(i);
                          }
                        }}
                        styles={{ input: { fontFamily: 'var(--font-mono)', fontWeight: 600, height: 48, fontSize: 17 } }}
                      />
                      <Box visibleFrom="sm" w={130}>
                        {numeric && flag && <ValueRange value={{ valueType: 'NUMERIC', numericValue: Number(raw), refLow: p.refLow, refHigh: p.refHigh, criticalLow: p.criticalLow, criticalHigh: p.criticalHigh, unit: p.unit }} />}
                      </Box>
                      <Box w={92} style={{ textAlign: 'right' }}><FlagBadge flag={flag} size="xs" /></Box>
                    </Group>
                  );
                })}
              </Stack>
            </Box>
          ))}

          {critical > 0 && (
            <Alert color="red" variant="light" radius="md" icon={<IconAlertOctagon size={18} />} title="Critical value">
              {critical} value{critical === 1 ? ' is' : 's are'} at a critical limit. Double-check the reading — the pathologist sees critical results first.
            </Alert>
          )}
          {enter.error && <Alert color="red" variant="light" radius="md">{friendlyMessage(enter.error)}</Alert>}
          <Group justify="space-between" wrap="wrap" gap="sm">
            <Text size="sm" c="var(--text-muted)">
              {abnormal > 0 ? `${abnormal} out of range` : 'All entered values are in range so far'}
            </Text>
            <Button size="lg" onClick={submit} loading={enter.isPending} leftSection={<IconSend size={18} />}>
              Send for verification
            </Button>
          </Group>
        </Stack>
      </Panel>
    </Stack>
  );
}
