'use client';

import { Alert, Button, Grid, Group, Pagination, SegmentedControl, Skeleton, Stack, Text, TextInput } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconAlertCircle, IconCircleCheck, IconDownload, IconFileText, IconReceipt, IconSearch } from '@tabler/icons-react';
import { useState } from 'react';
import { invoicePdfUrl, useInvoice, useInvoices, useMyInvoices } from '@/lib/billing';
import { friendlyMessage } from '@/lib/errors';
import { formatDate, formatMoney, formatRelative } from '@/lib/format';
import { EmptyState } from '@/components/ui/EmptyState';
import { GlowCard } from '@/components/ui/GlowCard';
import { PageTitle } from '@/components/ui/PageTitle';
import { Panel } from '@/components/ui/Panel';
import { Reveal } from '@/components/ui/Reveal';
import { ListRow } from '@/components/dashboard/widgets/ListRow';
import { DiscountPanel } from './DiscountPanel';
import { InvoiceCode, InvoiceLines, InvoiceStatus, InvoiceTotals, PaymentHistory } from './InvoiceBits';
import { PaymentPanel } from './PaymentPanel';

function ListSkeleton({ rows = 4, height = 56 }) {
  return <Stack gap="xs">{Array.from({ length: rows }, (_, i) => <Skeleton key={i} height={height} radius="md" />)}</Stack>;
}

/** A bill in a list: patient, what it covers, how old it is, what is still owed. */
export function InvoiceRow({ invoice, href, showPatient = true }) {
  const paid = invoice.status === 'PAID';
  return (
    <ListRow
      href={href}
      title={showPatient ? invoice.patientName : invoice.labOnly ? 'Lab tests' : `Visit — ${invoice.doctorName}`}
      subtitle={[
        invoice.invoiceCode,
        showPatient ? (invoice.labOnly ? 'Lab tests' : invoice.doctorName) : null,
        `${invoice.lineCount} item${invoice.lineCount === 1 ? '' : 's'}`,
        formatRelative(invoice.createdAt),
      ].filter(Boolean).join(' · ')}
      right={
        <Group gap="sm" wrap="nowrap">
          <div style={{ textAlign: 'right' }}>
            <Text size="sm" fw={700} className="mono">{formatMoney(paid ? invoice.net : invoice.balance)}</Text>
            <Text size="xs" c="var(--text-subtle)">{paid ? 'paid' : Number(invoice.amountPaid) > 0 ? 'balance' : 'due'}</Text>
          </div>
          <InvoiceStatus status={invoice.status} />
        </Group>
      }
    />
  );
}

// ------------------------------------------------------------------ front desk / admin

/** The billing counter: outstanding bills first (oldest waiting longest), searchable by patient or invoice number. */
export function BillingCounterView({ role }) {
  const [status, setStatus] = useState('OUTSTANDING');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [debounced] = useDebouncedValue(query.trim(), 250);
  const list = useInvoices(status, debounced, page - 1);
  const totalPages = list.data ? Math.ceil(list.data.totalElements / list.data.size) : 0;
  const base = role === 'ADMIN' ? '/admin/billing' : '/reception/billing';

  return (
    <Stack gap="xl">
      <PageTitle
        title="Billing"
        subtitle="Bills are created when a visit is finished or tests are ordered. Find one, take payment, give a discount."
        actions={
          <SegmentedControl
            value={status}
            onChange={(v) => { setStatus(v); setPage(1); }}
            data={[{ label: 'To collect', value: 'OUTSTANDING' }, { label: 'Paid', value: 'PAID' }, { label: 'All', value: 'ALL' }]}
          />
        }
      />
      <GlowCard p="lg">
        <TextInput
          size="md"
          radius="lg"
          placeholder="Search by patient name, patient ID or invoice number (INV-…)"
          leftSection={<IconSearch size={18} stroke={1.7} />}
          value={query}
          onChange={(e) => { setQuery(e.currentTarget.value); setPage(1); }}
          aria-label="Search invoices"
          mb="md"
        />
        {list.isPending ? (
          <ListSkeleton />
        ) : list.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(list.error)}</Text>
        ) : list.data.content.length === 0 ? (
          <EmptyState icon={status === 'OUTSTANDING' && !debounced ? IconCircleCheck : IconReceipt} title={status === 'OUTSTANDING' && !debounced ? 'Nothing to collect' : 'No invoices found'}>
            {debounced ? 'Check the spelling, or try the invoice number.' : status === 'OUTSTANDING' ? 'Every bill is settled.' : 'Invoices appear here as visits are finished.'}
          </EmptyState>
        ) : (
          <Stack gap={2}>
            {list.data.content.map((inv) => <InvoiceRow key={inv.id} invoice={inv} href={`${base}/${inv.id}`} />)}
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

function InvoiceLoadError({ error }) {
  return (
    <Alert color="red" variant="light" radius="lg" icon={<IconAlertCircle size={18} />} title="Couldn't open this invoice">
      {error?.status === 404 ? "This invoice doesn't exist or isn't yours to see." : friendlyMessage(error)}
    </Alert>
  );
}

function PdfActions({ id }) {
  return (
    <Group gap="sm">
      <Button component="a" href={invoicePdfUrl(id)} target="_blank" rel="noopener" variant="default" leftSection={<IconFileText size={16} />}>
        View PDF
      </Button>
      <Button component="a" href={invoicePdfUrl(id, true)} variant="default" leftSection={<IconDownload size={16} />}>
        Download
      </Button>
    </Group>
  );
}

function InvoiceCard({ invoice }) {
  return (
    <GlowCard p="lg">
      <Group justify="space-between" align="flex-start" wrap="wrap" gap="sm" mb="md">
        <div>
          <Text fw={600} fz={18}>{invoice.patient.fullName}</Text>
          <Text size="sm" c="var(--text-muted)" className="mono">{invoice.patient.patientCode}</Text>
          <Text size="sm" c="var(--text-muted)" mt={4}>
            {invoice.labOrderCode ? `Lab order ${invoice.labOrderCode}` : `Visit with ${invoice.doctorName}`} · {formatDate(invoice.createdAt)}
          </Text>
        </div>
        <Group gap={6}><InvoiceCode code={invoice.invoiceCode} /><InvoiceStatus status={invoice.status} /></Group>
      </Group>
      <Stack gap="md">
        <InvoiceLines invoice={invoice} />
        <InvoiceTotals invoice={invoice} />
        <PaymentHistory payments={invoice.payments} />
      </Stack>
    </GlowCard>
  );
}

/** One invoice for the front desk (and admins): the bill, then take payment and give a discount. */
export function CounterInvoiceView({ id, role }) {
  const query = useInvoice(id);
  if (query.isPending) return <ListSkeleton rows={2} height={220} />;
  if (query.isError) return <InvoiceLoadError error={query.error} />;
  const invoice = query.data;
  const open = invoice.status === 'UNPAID' || invoice.status === 'PARTIALLY_PAID';
  const base = role === 'ADMIN' ? '/admin/billing' : '/reception/billing';
  return (
    <Stack gap="xl">
      <PageTitle title="Invoice" back={{ href: base, label: 'All bills' }} actions={<PdfActions id={invoice.id} />} />
      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: open && role === 'RECEPTIONIST' ? 7 : 12 }}>
          <Reveal><InvoiceCard invoice={invoice} /></Reveal>
        </Grid.Col>
        {open && (
          <Grid.Col span={{ base: 12, md: 5 }}>
            <Stack gap="lg">
              {role === 'RECEPTIONIST' && <Reveal delay={0.04}><PaymentPanel invoice={invoice} /></Reveal>}
              <Reveal delay={0.08}><DiscountPanel invoice={invoice} role={role} /></Reveal>
            </Stack>
          </Grid.Col>
        )}
      </Grid>
    </Stack>
  );
}

// ------------------------------------------------------------------ patient

export function PatientBillsView() {
  const bills = useMyInvoices();
  const owed = bills.data?.filter((b) => b.status === 'UNPAID' || b.status === 'PARTIALLY_PAID') ?? [];
  const total = owed.reduce((sum, b) => sum + Number(b.balance), 0);
  return (
    <Stack gap="xl">
      <PageTitle
        title="Bills"
        subtitle={owed.length ? `${formatMoney(total)} to pay at the front desk.` : 'Your invoices for visits and lab tests.'}
      />
      <Panel title={bills.data ? `${bills.data.length} invoice${bills.data.length === 1 ? '' : 's'}` : 'Invoices'}>
        {bills.isPending ? (
          <ListSkeleton rows={2} />
        ) : bills.isError ? (
          <Text c="var(--critical)" size="sm">{friendlyMessage(bills.error)}</Text>
        ) : bills.data.length === 0 ? (
          <EmptyState icon={IconReceipt} title="No bills yet">
            After a visit or a lab test, your invoice appears here — you can download it as a PDF.
          </EmptyState>
        ) : (
          <Stack gap={2}>
            {bills.data.map((b) => <InvoiceRow key={b.id} invoice={b} href={`/patient/bills/${b.id}`} showPatient={false} />)}
          </Stack>
        )}
      </Panel>
    </Stack>
  );
}

export function PatientInvoiceView({ id }) {
  const query = useInvoice(id);
  if (query.isPending) return <ListSkeleton rows={2} height={220} />;
  if (query.isError) return <InvoiceLoadError error={query.error} />;
  const invoice = query.data;
  return (
    <Stack gap="xl">
      <PageTitle title="Invoice" back={{ href: '/patient/bills', label: 'All bills' }} actions={<PdfActions id={invoice.id} />} />
      <Reveal><InvoiceCard invoice={invoice} /></Reveal>
      {Number(invoice.balance) > 0 && (
        <Text size="sm" c="var(--text-muted)">
          You can pay at the front desk by cash, card or UPI. Show the invoice number <span className="mono">{invoice.invoiceCode}</span>.
        </Text>
      )}
    </Stack>
  );
}
