import { ReportPage } from '@/components/reports/ReportReader';

export const metadata = { title: 'Lab report' };

export default async function DoctorReportPage({ params }) {
  const { sampleId } = await params;
  return <ReportPage sampleId={sampleId} audience="doctor" back={{ href: '/doctor/reports', label: 'All reports' }} />;
}
