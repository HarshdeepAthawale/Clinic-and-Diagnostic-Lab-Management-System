import { LabOrderDetailView } from '@/components/lab/views';

export const metadata = { title: 'Lab order' };

export default async function LabOrderPage({ params }) {
  const { id } = await params;
  return <LabOrderDetailView id={id} back={{ href: '/lab/orders', label: 'All orders' }} labView />;
}
