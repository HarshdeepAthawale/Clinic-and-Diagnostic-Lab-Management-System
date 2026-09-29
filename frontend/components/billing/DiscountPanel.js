'use client';

import { Alert, Box, Button, Group, NumberInput, Stack, Text, TextInput, UnstyledButton } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconDiscount2 } from '@tabler/icons-react';
import { useState } from 'react';
import { discountError, discountPresets, useDiscountInvoice } from '@/lib/billing';
import { friendlyMessage } from '@/lib/errors';
import { formatMoney } from '@/lib/format';
import { Panel } from '@/components/ui/Panel';

/**
 * Give or remove a discount. It needs a reason, and the front desk is held to a share of the bill;
 * an admin can go further. Whoever applies it is recorded and shown on the invoice.
 */
export function DiscountPanel({ invoice, role }) {
  const discount = useDiscountInvoice(invoice.id);
  const canExceedCap = role === 'ADMIN';
  const cap = Number(invoice.discountCapPercent);
  const current = invoice.discount;
  const [amount, setAmount] = useState(current ? Number(current.amount) : 0);
  const [reason, setReason] = useState(current?.reason ?? '');
  const [touched, setTouched] = useState(false);

  const args = { amount, reason, gross: invoice.gross, paid: invoice.amountPaid, capPercent: cap, canExceedCap };
  const error = touched ? discountError(args) : null;
  const presets = discountPresets(invoice.gross, cap, canExceedCap);

  const send = (value, why) =>
    discount.mutate(
      { amount: value, reason: why || null },
      {
        onSuccess: (updated) => {
          notifications.show({
            title: value > 0 ? 'Discount applied' : 'Discount removed',
            message: `${updated.invoiceCode} total is now ${formatMoney(updated.net)}.`,
            color: 'teal',
            radius: 'lg',
            icon: <IconDiscount2 size={18} />,
          });
          setTouched(false);
        },
      },
    );

  const submit = () => {
    setTouched(true);
    if (discountError(args)) return;
    send(Number(amount) || 0, reason.trim());
  };

  return (
    <Panel
      title="Discount"
      subtitle={canExceedCap ? 'Any amount up to the bill' : `Front desk: up to ${cap}% of the bill`}
    >
      <Stack gap="md">
        {current && (
          <Box p="sm" style={{ borderRadius: 'var(--radius-sm)', background: 'var(--success-soft)' }}>
            <Text size="sm" fw={600} c="var(--success)">{formatMoney(current.amount)} given by {current.appliedBy}</Text>
            <Text size="xs" c="var(--text-muted)">{current.reason}</Text>
          </Box>
        )}
        <Group gap={6}>
          {presets.map((p) => (
            <UnstyledButton
              key={p.percent}
              onClick={() => setAmount(p.amount)}
              style={{ fontSize: 13, fontWeight: 600, padding: '6px 12px', borderRadius: 999, border: '1px solid var(--border-strong)', background: amount === p.amount ? 'var(--accent-soft)' : 'var(--surface)', color: amount === p.amount ? 'var(--accent)' : 'var(--text)' }}
            >
              {p.percent}% · {formatMoney(p.amount)}
            </UnstyledButton>
          ))}
        </Group>
        <NumberInput
          label="Discount (₹)"
          value={amount}
          onChange={setAmount}
          min={0}
          max={Number(invoice.gross)}
          decimalScale={2}
          hideControls
          error={error && !error.includes('why') ? error : null}
          styles={{ input: { fontFamily: 'var(--font-mono)', fontWeight: 600 } }}
        />
        <TextInput
          label="Reason"
          description="Recorded with your name on the invoice."
          placeholder="e.g. Senior citizen, staff family, repeat patient"
          maxLength={300}
          value={reason}
          onChange={(e) => setReason(e.currentTarget.value)}
          error={error?.includes('why') ? error : null}
        />
        {discount.error && (
          <Alert color="red" variant="light" radius="md" icon={<IconAlertCircle size={16} />}>{friendlyMessage(discount.error)}</Alert>
        )}
        <Group gap="sm">
          <Button variant="default" onClick={submit} loading={discount.isPending} leftSection={<IconDiscount2 size={16} />}>
            {current ? 'Update discount' : 'Apply discount'}
          </Button>
          {current && (
            <Button variant="subtle" color="gray" onClick={() => { setAmount(0); setReason(''); send(0, null); }} disabled={discount.isPending}>
              Remove
            </Button>
          )}
        </Group>
      </Stack>
    </Panel>
  );
}
