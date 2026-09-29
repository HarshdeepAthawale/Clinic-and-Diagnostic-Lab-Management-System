'use client';

import { Alert, Button, Drawer, Group, NumberInput, SegmentedControl, Skeleton, Stack, Text, Textarea } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconCheck, IconPencil } from '@tabler/icons-react';
import { useState } from 'react';
import { friendlyMessage } from '@/lib/errors';
import { formatRelative } from '@/lib/format';
import { adjustProblem, categoryLabel, deltaFor, formatDelta, REASON_LABELS, REASONS, useAdjustStock, useMovements } from '@/lib/inventory';
import { StockBadge, StockBar, StockCount } from './InventoryBits';

const REASON_OPTIONS = Object.entries(REASONS).map(([value, r]) => ({ value, label: r.label }));

/** Record a change: pick why, enter how many, and (for corrections) which way. The server has the final say. */
function AdjustForm({ item }) {
  const adjust = useAdjustStock(item.id);
  const [reason, setReason] = useState('USED');
  const [quantity, setQuantity] = useState('');
  const [direction, setDirection] = useState('1');
  const [note, setNote] = useState('');
  const [touched, setTouched] = useState(false);

  const problem = adjustProblem({ reason, quantity, direction: Number(direction), item });
  const delta = deltaFor(reason, quantity, Number(direction));
  const showProblem = touched && problem;

  const submit = (event) => {
    event.preventDefault();
    setTouched(true);
    if (problem) return;
    adjust.mutate(
      { delta, reason, note: note.trim() || null },
      {
        onSuccess: (updated) => {
          notifications.show({
            title: `${item.name}: ${updated.currentStock} ${updated.unit} now`,
            message: `${REASON_LABELS[reason]} ${formatDelta(delta)}`,
            color: 'teal',
            radius: 'lg',
            icon: <IconCheck size={18} />,
          });
          setQuantity('');
          setNote('');
          setTouched(false);
        },
      },
    );
  };

  return (
    <form onSubmit={submit}>
      <Stack gap="sm">
        <SegmentedControl fullWidth size="xs" value={reason} onChange={setReason} data={REASON_OPTIONS} aria-label="Reason for the change" />
        <Text size="xs" c="var(--text-muted)">{REASONS[reason].hint}</Text>
        <Group gap="sm" align="flex-start" wrap="nowrap">
          {REASONS[reason].sign === 0 && (
            <SegmentedControl size="xs" value={direction} onChange={setDirection} data={[{ value: '1', label: 'Add' }, { value: '-1', label: 'Remove' }]} aria-label="Direction" />
          )}
          <NumberInput
            style={{ flex: 1 }}
            placeholder="How many"
            aria-label="Quantity"
            min={1}
            max={1_000_000}
            allowDecimal={false}
            allowNegative={false}
            hideControls
            value={quantity}
            onChange={setQuantity}
            rightSection={<Text size="xs" c="var(--text-muted)" pr={8}>{item.unit}</Text>}
            rightSectionWidth={64}
            error={showProblem ? problem : undefined}
          />
        </Group>
        <Textarea placeholder="Note (optional) — batch number, who used it, why" maxLength={300} autosize minRows={1} maxRows={3} value={note} onChange={(e) => setNote(e.currentTarget.value)} />
        {adjust.isError && (
          <Alert color="red" variant="light" icon={<IconAlertCircle size={16} />}>
            {friendlyMessage(adjust.error)}
          </Alert>
        )}
        <Button type="submit" loading={adjust.isPending} disabled={!item.active}>
          {delta !== null && !problem ? `Record ${formatDelta(delta)} ${item.unit}` : 'Record change'}
        </Button>
        {!item.active && <Text size="xs" c="var(--text-muted)">This item is retired, so its level can&apos;t change.</Text>}
      </Stack>
    </form>
  );
}

/** Newest first: what changed, why, the level after it, and who did it. */
function History({ itemId }) {
  const movements = useMovements(itemId);
  if (movements.isPending) return <Skeleton height={80} radius="md" />;
  if (movements.isError) return <Text size="sm" c="var(--critical)">{friendlyMessage(movements.error)}</Text>;
  if (movements.data.length === 0) return <Text size="sm" c="var(--text-muted)">No changes recorded yet.</Text>;
  return (
    <Stack gap={0}>
      {movements.data.map((m) => (
        <Group key={m.id} justify="space-between" wrap="nowrap" py={9} style={{ borderTop: '1px solid var(--border)' }}>
          <div style={{ minWidth: 0 }}>
            <Text size="sm" fw={600}>
              {REASON_LABELS[m.reason]}{' '}
              <Text span className="mono" c={m.delta > 0 ? 'var(--success)' : 'var(--text)'}>{formatDelta(m.delta)}</Text>
            </Text>
            <Text size="xs" c="var(--text-muted)" truncate>
              {[m.actorName, formatRelative(m.createdAt)].filter(Boolean).join(' · ')}
            </Text>
            {m.note && <Text size="xs" c="var(--text-subtle)">{m.note}</Text>}
          </div>
          <Text size="xs" c="var(--text-muted)" className="mono" style={{ whiteSpace: 'nowrap' }}>→ {m.stockAfter}</Text>
        </Group>
      ))}
    </Stack>
  );
}

/** One item: its level, a form to record a change, and the history of changes. Admins can edit details. */
export function ItemPanel({ item, opened, onClose, onEdit }) {
  return (
    <Drawer opened={opened} onClose={onClose} position="right" size="md" radius="md" title={item?.name} padding="lg">
      {item && (
        <Stack gap="lg">
          <div>
            <Group justify="space-between" align="flex-end" mb={8}>
              <div>
                <Text size="xs" c="var(--text-muted)">{categoryLabel(item.category)}</Text>
                <StockCount item={item} size={34} />
              </div>
              <StockBadge item={item} />
            </Group>
            <StockBar item={item} width="100%" />
            <Text size="xs" c="var(--text-muted)" mt={6}>
              {item.lowStockThreshold ? `Flagged below ${item.lowStockThreshold} ${item.unit}` : 'Not watched for low stock'} · updated {formatRelative(item.updatedAt)}
            </Text>
          </div>
          <AdjustForm item={item} />
          <div>
            <Group justify="space-between" mb={4}>
              <Text fw={600}>History</Text>
              {onEdit && (
                <Button size="compact-xs" variant="subtle" color="gray" leftSection={<IconPencil size={13} />} onClick={onEdit}>
                  Edit details
                </Button>
              )}
            </Group>
            <History itemId={item.id} />
          </div>
        </Stack>
      )}
    </Drawer>
  );
}
