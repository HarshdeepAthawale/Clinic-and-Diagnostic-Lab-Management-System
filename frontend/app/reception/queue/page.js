import { QueueView } from '@/components/appointments/views';

export const metadata = { title: 'Live queue' };

export default function ReceptionQueuePage() {
  return <QueueView role="RECEPTIONIST" />;
}
