import { CounterInvoiceView } from '@/components/billing/views';

export const metadata = { title: 'Invoice' };

export default async function ReceptionInvoicePage({ params }) {
  const { id } = await params;
  return <CounterInvoiceView id={id} role="RECEPTIONIST" />;
}
