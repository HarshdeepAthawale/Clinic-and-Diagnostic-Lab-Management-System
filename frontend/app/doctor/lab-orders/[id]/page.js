import { LabOrderDetailView } from '@/components/lab/views';

export const metadata = { title: 'Lab order' };

export default async function DoctorLabOrderPage({ params }) {
  const { id } = await params;
  return <LabOrderDetailView id={id} back={{ href: '/doctor/patients', label: 'Patients' }} />;
}
