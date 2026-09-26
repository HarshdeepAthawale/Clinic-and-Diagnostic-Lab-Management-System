import { StaffShell } from '@/components/shell/StaffShell';

export const metadata = { title: 'Today' };

export default function DoctorLayout({ children }) {
  return <StaffShell role="DOCTOR">{children}</StaffShell>;
}
