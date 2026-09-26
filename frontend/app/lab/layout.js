import { StaffShell } from '@/components/shell/StaffShell';

export const metadata = { title: 'Bench' };

export default function LabLayout({ children }) {
  return <StaffShell role="LAB_TECHNICIAN">{children}</StaffShell>;
}
