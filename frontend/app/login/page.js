import { Suspense } from 'react';
import { AuthLayout } from '@/components/auth/AuthLayout';
import { LoginForm } from './LoginForm';

export const metadata = { title: 'Sign in' };

export default function LoginPage() {
  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to continue to your workspace.">
      <Suspense>
        <LoginForm />
      </Suspense>
    </AuthLayout>
  );
}
