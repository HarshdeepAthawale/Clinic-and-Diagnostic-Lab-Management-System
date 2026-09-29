import { WorkspaceShell } from '@/components/shell/WorkspaceShell';

export const metadata = { title: 'Overview' };

export default function AdminLayout({ children }) {
  return <WorkspaceShell role="ADMIN">{children}</WorkspaceShell>;
}
