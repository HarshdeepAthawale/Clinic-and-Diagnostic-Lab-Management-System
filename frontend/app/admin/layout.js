import { StaffShell } from '@/components/shell/StaffShell';

export const metadata = { title: 'Insights' };

export default function AdminLayout({ children }) {
  return <StaffShell role="ADMIN">{children}</StaffShell>;
}
