import { ReviewMode } from '@/components/pathology/ReviewMode';

export const metadata = { title: 'Review result' };

export default async function PathologyReviewPage({ params }) {
  const { id } = await params;
  return <ReviewMode id={id} />;
}
