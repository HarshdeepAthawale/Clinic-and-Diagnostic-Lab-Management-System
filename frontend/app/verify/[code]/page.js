import { ReportCheck } from '@/components/reports/ReportCheck';

export const metadata = { title: 'Report check', robots: { index: false, follow: false } };

export default async function VerifyReportPage({ params }) {
  const { code } = await params;
  return <ReportCheck code={code} />;
}
