import { ReportPage } from '@/components/reports/ReportReader';

export const metadata = { title: 'Lab report' };

export default async function PatientReportPage({ params }) {
  const { sampleId } = await params;
  return <ReportPage sampleId={sampleId} audience="patient" back={{ href: '/patient/reports', label: 'All reports' }} />;
}
