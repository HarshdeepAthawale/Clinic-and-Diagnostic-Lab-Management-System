import { StaffShell } from '@/components/shell/StaffShell';

export const metadata = { title: 'Today' };

export default function ReceptionLayout({ children }) {
  return <StaffShell role="RECEPTIONIST">{children}</StaffShell>;
}
