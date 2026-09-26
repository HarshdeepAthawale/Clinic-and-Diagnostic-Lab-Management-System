import { AuthLayout } from '@/components/auth/AuthLayout';
import { RegisterForm } from './RegisterForm';

export const metadata = { title: 'Create account' };

export default function RegisterPage() {
  return (
    <AuthLayout
      title="Create your account"
      subtitle="Book visits, track your lab tests and download reports — all in one place."
    >
      <RegisterForm />
    </AuthLayout>
  );
}
