import { PatientPrescriptionView } from '@/components/consultations/views';

export const metadata = { title: 'Prescription' };

export default async function PatientPrescriptionPage({ params }) {
  const { id } = await params;
  return <PatientPrescriptionView id={id} />;
}
