import { DoctorConsultationView } from '@/components/consultations/views';

export const metadata = { title: 'Consultation' };

export default async function DoctorConsultationPage({ params }) {
  const { id } = await params;
  return <DoctorConsultationView id={id} />;
}
