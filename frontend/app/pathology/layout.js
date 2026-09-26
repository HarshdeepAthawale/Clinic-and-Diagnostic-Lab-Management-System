import { StaffShell } from '@/components/shell/StaffShell';

export const metadata = { title: 'Today' };

export default function PathologyLayout({ children }) {
  return <StaffShell role="PATHOLOGIST">{children}</StaffShell>;
}
