import { WorkspaceShell } from '@/components/shell/WorkspaceShell';

export const metadata = { title: 'Today' };

export default function PathologyLayout({ children }) {
  return <WorkspaceShell role="PATHOLOGIST">{children}</WorkspaceShell>;
}
