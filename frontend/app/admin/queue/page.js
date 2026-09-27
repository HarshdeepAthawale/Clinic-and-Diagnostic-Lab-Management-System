import { QueueView } from '@/components/appointments/views';

export const metadata = { title: 'Live queue' };

export default function AdminQueuePage() {
  return <QueueView role="ADMIN" />;
}
