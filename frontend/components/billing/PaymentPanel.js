'use client';

import { Alert, Button, Group, NumberInput, SegmentedControl, Stack, Text, TextInput, UnstyledButton } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconCash } from '@tabler/icons-react';
import { useState } from 'react';
import { PAYMENT_METHODS, paymentError, usePayInvoice } from '@/lib/billing';
import { friendlyMessage } from '@/lib/errors';
import { formatMoney } from '@/lib/format';
import { Panel } from '@/components/ui/Panel';

/** Take money at the counter: the full balance in one tap, or a part payment. Never more than is owed. */
export function PaymentPanel({ invoice }) {
  const pay = usePayInvoice(invoice.id);
  const [amount, setAmount] = useState(Number(invoice.balance));
  const [method, setMethod] = useState('CASH');
  const [reference, setReference] = useState('');
  const [touched, setTouched] = useState(false);

  const balance = Number(invoice.balance);
  const error = touched ? paymentError(amount, balance) : null;

  const submit = () => {
    setTouched(true);
    if (paymentError(amount, balance)) return;
    pay.mutate(
      { amount, method, reference: reference.trim() || null },
      {
        onSuccess: (updated) => {
          notifications.show({
            title: updated.status === 'PAID' ? 'Paid in full' : 'Payment recorded',
            message: `${formatMoney(amount)} received for ${updated.invoiceCode}.`,
            color: 'teal',
            radius: 'lg',
            icon: <IconCash size={18} />,
          });
          setAmount(Number(updated.balance));
          setReference('');
          setTouched(false);
        },
      },
    );
  };

  return (
    <Panel title="Take payment" subtitle={`Balance ${formatMoney(balance)}`}>
      <Stack gap="md">
        <Group align="flex-end" gap="sm" wrap="wrap">
          <NumberInput
            label="Amount (₹)"
            value={amount}
            onChange={(v) => setAmount(v)}
            min={0}
            max={balance}
            decimalScale={2}
            hideControls
            error={error}
            w={{ base: '100%', xs: 180 }}
            styles={{ input: { fontFamily: 'var(--font-mono)', fontWeight: 600 } }}
          />
          <UnstyledButton
            onClick={() => setAmount(balance)}
            style={{ fontSize: 13, fontWeight: 600, padding: '8px 12px', borderRadius: 999, border: '1px solid var(--border-strong)', background: amount === balance ? 'var(--accent-soft)' : 'var(--surface)', color: amount === balance ? 'var(--accent)' : 'var(--text)' }}
          >
            Full balance
          </UnstyledButton>
          {balance >= 2 && (
            <UnstyledButton
              onClick={() => setAmount(Math.round(balance * 50) / 100)}
              style={{ fontSize: 13, fontWeight: 600, padding: '8px 12px', borderRadius: 999, border: '1px solid var(--border-strong)', background: 'var(--surface)' }}
            >
              Half
            </UnstyledButton>
          )}
        </Group>
        <div>
          <Text size="sm" fw={500} mb={6}>Paid by</Text>
          <SegmentedControl value={method} onChange={setMethod} data={PAYMENT_METHODS} />
        </div>
        {method !== 'CASH' && (
          <TextInput
            label={method === 'UPI' ? 'UPI transaction ID' : 'Card receipt / last 4 digits'}
            placeholder="Optional"
            maxLength={60}
            value={reference}
            onChange={(e) => setReference(e.currentTarget.value)}
          />
        )}
        {pay.error && (
          <Alert color="red" variant="light" radius="md" icon={<IconAlertCircle size={16} />}>{friendlyMessage(pay.error)}</Alert>
        )}
        <Button onClick={submit} loading={pay.isPending} leftSection={<IconCash size={16} />}>
          Record {formatMoney(Number(amount) || 0)} {method === 'CASH' ? 'cash' : method === 'UPI' ? 'UPI' : 'card'} payment
        </Button>
      </Stack>
    </Panel>
  );
}
