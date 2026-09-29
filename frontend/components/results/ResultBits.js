import { Box, Group, Stack, Text } from '@mantine/core';
import { flagLabel, flagStatus, formatRange, formatValue, isAbnormal } from '@/lib/results';
import { RangeBar } from '@/components/ui/RangeBar';
import { StatusBadge } from '@/components/ui/StatusBadge';

/** Normal / Low / High / Critical as the app's one status badge; nothing for text results (no flag). */
export function FlagBadge({ flag, size = 'sm' }) {
  if (!flag) return null;
  return <StatusBadge status={flagStatus(flag)} label={flagLabel(flag)} size={size} />;
}

/** A value with its unit, tinted when it is out of range. */
export function ValueText({ value, size = 'md', mono = true }) {
  const tone = value.flag === 'CRITICAL_LOW' || value.flag === 'CRITICAL_HIGH' ? 'var(--critical)' : isAbnormal(value.flag) ? 'var(--warning)' : undefined;
  return (
    <Text component="span" fw={700} size={size} c={tone} className={mono ? 'mono' : undefined}>
      {formatValue(value)}
      {value.unit && <Text span size="xs" fw={500} c="var(--text-muted)"> {value.unit}</Text>}
    </Text>
  );
}

/** The range bar when a value has both ends of a numeric range; otherwise the range as text. */
export function ValueRange({ value, width = 120 }) {
  const both = value.valueType === 'NUMERIC' && value.refLow !== null && value.refLow !== undefined
    && value.refHigh !== null && value.refHigh !== undefined && value.numericValue !== null && value.numericValue !== undefined;
  if (both) {
    return (
      <RangeBar
        value={Number(value.numericValue)}
        low={Number(value.refLow)}
        high={Number(value.refHigh)}
        criticalLow={value.criticalLow == null ? undefined : Number(value.criticalLow)}
        criticalHigh={value.criticalHigh == null ? undefined : Number(value.criticalHigh)}
        unit={value.unit ?? ''}
        width={width}
      />
    );
  }
  return <Text size="xs" c="var(--text-subtle)" className="mono">{formatRange(value)}</Text>;
}

/**
 * Values grouped by the test they belong to, in the order given by `tests` ({ itemId, name, code }).
 * Values whose test isn't listed are kept, under their own group, rather than dropped.
 */
export function groupValues(tests, values) {
  const groups = tests.map((t) => ({ key: t.itemId, title: t.name ?? t.testName, code: t.code ?? t.testCode, values: [] }));
  const byKey = new Map(groups.map((g) => [g.key, g]));
  for (const v of values) {
    let group = byKey.get(v.itemId);
    if (!group) {
      group = { key: v.itemId, title: 'Other', code: '', values: [] };
      byKey.set(v.itemId, group);
      groups.push(group);
    }
    group.values.push(v);
  }
  return groups.filter((g) => g.values.length > 0);
}

/** A table of results per test: parameter, value, where it sits in its range, the range and the flag. */
export function ResultTable({ groups, compact = false }) {
  return (
    <Stack gap={compact ? 'md' : 'lg'}>
      {groups.map((g) => (
        <Box key={g.key}>
          <Group gap={8} mb={6}>
            <Text fw={600} size="sm">{g.title}</Text>
            {g.code && <Text size="xs" className="mono" c="var(--text-subtle)">{g.code}</Text>}
          </Group>
          <Stack gap={0}>
            {g.values.map((v) => (
              <Group key={v.parameterId} wrap="nowrap" gap="md" py={compact ? 8 : 10} style={{ borderTop: '1px solid var(--border)' }}>
                <Text size="sm" style={{ flex: '1 1 0', minWidth: 0 }}>{v.name}</Text>
                <div style={{ minWidth: 92, textAlign: 'right' }}><ValueText value={v} size="sm" /></div>
                <Box visibleFrom="xs" w={132}><ValueRange value={v} /></Box>
                <Box w={92} style={{ textAlign: 'right' }}><FlagBadge flag={v.flag} size="xs" /></Box>
              </Group>
            ))}
          </Stack>
        </Box>
      ))}
    </Stack>
  );
}
