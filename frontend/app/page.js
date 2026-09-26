import { redirect } from 'next/navigation';

// proxy.js normally redirects "/" to the user's workspace or /login; this is the fallback.
export default function RootPage() {
  redirect('/login');
}
