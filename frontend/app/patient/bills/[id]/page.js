import { PatientInvoiceView } from '@/components/billing/views';

export const metadata = { title: 'Invoice' };

export default async function PatientInvoicePage({ params }) {
  const { id } = await params;
  return <PatientInvoiceView id={id} />;
}
