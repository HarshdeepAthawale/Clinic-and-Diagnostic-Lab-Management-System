import { WorkspaceShell } from '@/components/shell/WorkspaceShell';

export const metadata = { title: 'Today' };

export default function ReceptionLayout({ children }) {
  return <WorkspaceShell role="RECEPTIONIST">{children}</WorkspaceShell>;
}
