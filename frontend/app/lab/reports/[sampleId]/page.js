import { ReportPage } from '@/components/reports/ReportReader';

export const metadata = { title: 'Lab report' };

export default async function LabReportPage({ params }) {
  const { sampleId } = await params;
  return <ReportPage sampleId={sampleId} audience="lab" back={{ href: '/lab/reports', label: 'Reports to send' }} />;
}
