import { WorkspaceShell } from '@/components/shell/WorkspaceShell';

export const metadata = { title: 'Today' };

export default function DoctorLayout({ children }) {
  return <WorkspaceShell role="DOCTOR">{children}</WorkspaceShell>;
}
