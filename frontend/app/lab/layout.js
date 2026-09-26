import { WorkspaceShell } from '@/components/shell/WorkspaceShell';

export const metadata = { title: 'Bench' };

export default function LabLayout({ children }) {
  return <WorkspaceShell role="LAB_TECHNICIAN">{children}</WorkspaceShell>;
}
