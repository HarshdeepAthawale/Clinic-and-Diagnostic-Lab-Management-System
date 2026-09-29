'use client';

import { Box, Group, Stack, Text } from '@mantine/core';
import { IconCircleCheck, IconCash, IconReceipt } from '@tabler/icons-react';
import Link from 'next/link';
import { formatMoney } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { Panel } from '@/components/ui/Panel';
import { InvoiceRow } from '@/components/billing/views';

const linkStyle = { fontSize: 13, fontWeight: 600, color: 'var(--accent)' };

/** Front desk and admin: unpaid and part-paid bills, oldest first, with the total still owed. */
export function OutstandingBills({ widget, role }) {
  const { invoices, count, amount } = widget.data;
  const base = role === 'ADMIN' ? '/admin/billing' : '/reception/billing';
  return (
    <Panel
      title={widget.title}
      subtitle={count ? `${count} open · ${formatMoney(amount)} to collect` : 'All bills are settled'}
      right={<Link href={base} style={linkStyle}>Billing</Link>}
    >
      {invoices.length === 0 ? (
        <EmptyState icon={IconCircleCheck} title="Nothing to collect" compact>
          Bills appear here when a visit is finished or tests are ordered.
        </EmptyState>
      ) : (
        <Stack gap={2}>
          {invoices.map((inv) => <InvoiceRow key={inv.id} invoice={inv} href={`${base}/${inv.id}`} />)}
        </Stack>
      )}
    </Panel>
  );
}

const METHODS = [['cash', 'Cash'], ['card', 'Card'], ['upi', 'UPI']];

/** Today's takings as a single figure and a proportional split by payment method. */
export function Collections({ widget }) {
  const c = widget.data;
  const total = Number(c.total);
  return (
    <Panel title={widget.title} subtitle={`${c.payments} payment${c.payments === 1 ? '' : 's'}`}>
      {total === 0 ? (
        <EmptyState icon={IconCash} title="Nothing collected yet" compact>Payments taken today add up here.</EmptyState>
      ) : (
        <Stack gap="md">
          <Text fz={30} fw={600} className="mono" lh={1.1}>{formatMoney(total)}</Text>
          <Group gap={3} wrap="nowrap" h={8} style={{ borderRadius: 999, overflow: 'hidden' }} aria-hidden>
            {METHODS.map(([key], i) =>
              Number(c[key]) > 0 ? <Box key={key} h="100%" style={{ flex: Number(c[key]), background: ['var(--ink)', 'var(--accent)', 'var(--border-strong)'][i] }} /> : null,
            )}
          </Group>
          <Stack gap={8}>
            {METHODS.map(([key, label], i) => (
              <Group key={key} justify="space-between">
                <Group gap={8}>
                  <Box w={8} h={8} style={{ borderRadius: 3, background: ['var(--ink)', 'var(--accent)', 'var(--border-strong)'][i] }} />
                  <Text size="sm">{label}</Text>
                </Group>
                <Text size="sm" fw={600} className="mono">{formatMoney(c[key])}</Text>
              </Group>
            ))}
          </Stack>
        </Stack>
      )}
    </Panel>
  );
}

/** Patient: their unpaid bills, with what to do. */
export function MyBills({ widget }) {
  const owed = widget.data.reduce((sum, b) => sum + Number(b.balance), 0);
  return (
    <GlowCard p="lg">
      <Group justify="space-between" mb="sm">
        <Group gap={8}>
          <IconReceipt size={18} color="var(--accent)" />
          <Text fw={600}>{widget.title}</Text>
          <Text size="sm" c="var(--text-muted)">· {formatMoney(owed)} in total</Text>
        </Group>
        <Link href="/patient/bills" style={linkStyle}>All bills</Link>
      </Group>
      <Stack gap={2}>
        {widget.data.map((b) => <InvoiceRow key={b.id} invoice={b} href={`/patient/bills/${b.id}`} showPatient={false} />)}
      </Stack>
      <Text size="xs" c="var(--text-subtle)" mt="sm">Pay at the front desk with cash, card or UPI.</Text>
    </GlowCard>
  );
}
