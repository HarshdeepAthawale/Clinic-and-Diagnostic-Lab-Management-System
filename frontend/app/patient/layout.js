import { PatientShell } from '@/components/shell/PatientShell';

export const metadata = { title: 'Home' };

export default function PatientLayout({ children }) {
  return <PatientShell>{children}</PatientShell>;
}
