import { WorkspaceShell } from '@/components/shell/WorkspaceShell';

export const metadata = { title: 'Insights' };

export default function AdminLayout({ children }) {
  return <WorkspaceShell role="ADMIN">{children}</WorkspaceShell>;
}
