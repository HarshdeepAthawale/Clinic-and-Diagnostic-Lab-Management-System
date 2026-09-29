import { BillingCounterView } from '@/components/billing/views';

export const metadata = { title: 'Billing' };

export default function ReceptionBillingPage() {
  return <BillingCounterView role="RECEPTIONIST" />;
}
