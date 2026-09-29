import { Badge, Box, Group, Stack, Text } from '@mantine/core';
import { INVOICE_STATUS } from '@/lib/billing';
import { formatDate, formatDateTime, formatMoney } from '@/lib/format';
import { StatusBadge } from '@/components/ui/StatusBadge';

export function InvoiceCode({ code }) {
  return (
    <Badge size="sm" radius="sm" variant="outline" color="dark" styles={{ root: { textTransform: 'none', fontFamily: 'var(--font-mono)' } }}>
      {code}
    </Badge>
  );
}

export function InvoiceStatus({ status }) {
  const s = INVOICE_STATUS[status] ?? { status, label: status };
  return <StatusBadge status={s.status} label={s.label} />;
}

/** One line per charge; voided lines stay visible, struck through, so the history is honest. */
export function InvoiceLines({ invoice }) {
  return (
    <Stack gap={0}>
      {invoice.lines.map((l) => (
        <Group key={l.id} wrap="nowrap" justify="space-between" py={10} style={{ borderTop: '1px solid var(--border)', opacity: l.voidedAt ? 0.5 : 1 }}>
          <div style={{ minWidth: 0 }}>
            <Text size="sm" fw={600} truncate td={l.voidedAt ? 'line-through' : undefined}>{l.description}</Text>
            {l.voidedAt && <Text size="xs" c="var(--text-subtle)">Removed {formatDate(l.voidedAt)}</Text>}
          </div>
          <Text size="sm" fw={600} className="mono" td={l.voidedAt ? 'line-through' : undefined}>{formatMoney(l.amount)}</Text>
        </Group>
      ))}
    </Stack>
  );
}

function Row({ label, value, strong, tone, hint }) {
  return (
    <Group justify="space-between" wrap="nowrap" align="baseline">
      <div>
        <Text size="sm" c={tone ?? 'var(--text-muted)'} fw={strong ? 700 : 500}>{label}</Text>
        {hint && <Text size="xs" c="var(--text-subtle)">{hint}</Text>}
      </div>
      <Text className="mono" fw={strong ? 700 : 600} fz={strong ? 20 : 'sm'} c={tone}>{value}</Text>
    </Group>
  );
}

/** Subtotal, discount (with who gave it and why), total, paid and what is still owed. */
export function InvoiceTotals({ invoice }) {
  const d = invoice.discount;
  return (
    <Stack gap={8} pt="sm" style={{ borderTop: '1px solid var(--border)' }}>
      <Row label="Subtotal" value={formatMoney(invoice.gross)} />
      {d && (
        <Row
          label="Discount"
          value={`− ${formatMoney(d.amount)}`}
          tone="var(--success)"
          hint={`${d.reason} · ${d.appliedBy} · ${formatDateTime(d.appliedAt)}`}
        />
      )}
      <Row label="Total" value={formatMoney(invoice.net)} strong />
      {Number(invoice.amountPaid) > 0 && <Row label="Paid" value={formatMoney(invoice.amountPaid)} />}
      {Number(invoice.balance) > 0 && <Row label="Balance due" value={formatMoney(invoice.balance)} strong tone="var(--accent)" />}
    </Stack>
  );
}

const METHOD_LABEL = { CASH: 'Cash', CARD: 'Card', UPI: 'UPI' };

export function PaymentHistory({ payments }) {
  if (payments.length === 0) return null;
  return (
    <Box>
      <Text size="xs" fw={700} tt="uppercase" c="var(--text-subtle)" mb={6} style={{ letterSpacing: '0.06em' }}>Payments received</Text>
      <Stack gap={0}>
        {payments.map((p) => (
          <Group key={p.id} justify="space-between" wrap="nowrap" py={8} style={{ borderTop: '1px solid var(--border)' }}>
            <div>
              <Text size="sm" fw={600}>{METHOD_LABEL[p.method] ?? p.method}{p.reference ? ` · ${p.reference}` : ''}</Text>
              <Text size="xs" c="var(--text-muted)">{formatDateTime(p.receivedAt)} · {p.receivedBy}</Text>
            </div>
            <Text size="sm" fw={600} className="mono">{formatMoney(p.amount)}</Text>
          </Group>
        ))}
      </Stack>
    </Box>
  );
}
