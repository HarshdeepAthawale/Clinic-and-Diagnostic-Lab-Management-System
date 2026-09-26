import { DoctorPatientView } from '@/components/patients/views';

export const metadata = { title: 'Medical record' };

export default async function DoctorPatientPage({ params }) {
  const { id } = await params;
  return <DoctorPatientView id={id} />;
}
