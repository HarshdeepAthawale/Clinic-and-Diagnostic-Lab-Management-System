import { WorkspaceShell } from '@/components/shell/WorkspaceShell';

export const metadata = { title: 'Home' };

export default function PatientLayout({ children }) {
  return <WorkspaceShell role="PATIENT">{children}</WorkspaceShell>;
}
