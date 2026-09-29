import { SampleWorkspace } from '@/components/lab/SampleWorkspace';

export const metadata = { title: 'Sample' };

export default async function LabSamplePage({ params }) {
  const { id } = await params;
  return <SampleWorkspace id={id} />;
}
