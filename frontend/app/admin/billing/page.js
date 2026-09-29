import { BillingCounterView } from '@/components/billing/views';

export const metadata = { title: 'Billing' };

export default function AdminBillingPage() {
  return <BillingCounterView role="ADMIN" />;
}
