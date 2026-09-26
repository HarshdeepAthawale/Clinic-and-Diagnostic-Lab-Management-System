import { ReceptionPatientView } from '@/components/patients/views';

export const metadata = { title: 'Patient' };

export default async function ReceptionPatientPage({ params }) {
  const { id } = await params;
  return <ReceptionPatientView id={id} />;
}
