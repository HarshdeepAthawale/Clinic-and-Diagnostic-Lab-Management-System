import {
  IconCalendarEvent,
  IconCash,
  IconChartBar,
  IconFileText,
  IconFlask,
  IconHistory,
  IconHome,
  IconId,
  IconListCheck,
  IconPackage,
  IconPrescription,
  IconReceipt,
  IconReportMedical,
  IconShieldLock,
  IconStethoscope,
  IconTestPipe,
  IconTicket,
  IconUserPlus,
  IconUserSearch,
  IconUsers,
} from '@tabler/icons-react';
import { ROLE_HOME } from './roleRoutes';

/**
 * Per-role workspace config (Docs/Design.md §3, §6). `nav` drives the navbar; `phase` marks
 * items not built yet (shown dimmed with a tooltip). `cta` is the white call-to-action pill.
 */
export const ROLES = {
  PATIENT: {
    key: 'PATIENT',
    label: 'Patient',
    home: ROLE_HOME.PATIENT,
    dashboardEndpoint: '/dashboard/patient',
    nav: [
      { label: 'Home', href: '/patient', icon: IconHome },
      { label: 'Appointments', href: '/patient/appointments', icon: IconCalendarEvent },
      { label: 'Prescriptions', href: '/patient/prescriptions', icon: IconPrescription },
      { label: 'Lab tests', href: '/patient/lab-tests', icon: IconFlask },
      { label: 'Reports', href: '/patient/reports', icon: IconFileText },
      { label: 'Bills', href: '/patient/bills', icon: IconReceipt },
      { label: 'My record', href: '/patient/profile', icon: IconId },
    ],
  },
  DOCTOR: {
    key: 'DOCTOR',
    label: 'Doctor',
    home: ROLE_HOME.DOCTOR,
    dashboardEndpoint: '/dashboard/doctor',
    cta: { label: 'Find patient', href: '/doctor/patients', icon: IconUserSearch },
    nav: [
      { label: 'Today', href: '/doctor', icon: IconHome },
      { label: 'Schedule', href: '/doctor/schedule', icon: IconCalendarEvent },
      { label: 'Patients', href: '/doctor/patients', icon: IconUsers },
      { label: 'Consultations', href: '/doctor/consultations', icon: IconStethoscope },
      { label: 'Lab reports', href: '/doctor/reports', icon: IconReportMedical },
    ],
  },
  PATHOLOGIST: {
    key: 'PATHOLOGIST',
    label: 'Pathologist',
    home: ROLE_HOME.PATHOLOGIST,
    dashboardEndpoint: '/dashboard/pathologist',
    nav: [
      { label: 'Today', href: '/pathology', icon: IconHome },
      { label: 'Verification queue', href: '/pathology/queue', icon: IconListCheck },
      { label: 'My verifications', href: '/pathology/history', icon: IconHistory },
    ],
  },
  RECEPTIONIST: {
    key: 'RECEPTIONIST',
    label: 'Receptionist',
    home: ROLE_HOME.RECEPTIONIST,
    dashboardEndpoint: '/dashboard/receptionist',
    cta: { label: 'Register patient', href: '/reception/register', icon: IconUserPlus },
    nav: [
      { label: 'Today', href: '/reception', icon: IconHome },
      { label: 'Live queue', href: '/reception/queue', icon: IconTicket },
      { label: 'Patients', href: '/reception/patients', icon: IconUsers },
      { label: 'Appointments', href: '/reception/appointments', icon: IconCalendarEvent },
      { label: 'Samples', href: '/reception/samples', icon: IconTestPipe },
      { label: 'Billing', href: '/reception/billing', icon: IconCash },
    ],
  },
  LAB_TECHNICIAN: {
    key: 'LAB_TECHNICIAN',
    label: 'Lab Technician',
    home: ROLE_HOME.LAB_TECHNICIAN,
    dashboardEndpoint: '/dashboard/lab-technician',
    nav: [
      { label: 'Bench', href: '/lab', icon: IconHome },
      { label: 'Orders', href: '/lab/orders', icon: IconFlask },
      { label: 'Samples', href: '/lab/samples', icon: IconTestPipe },
      { label: 'Reports', href: '/lab/reports', icon: IconFileText },
      { label: 'Inventory', href: '/lab/inventory', icon: IconPackage, phase: 9 },
    ],
  },
  ADMIN: {
    key: 'ADMIN',
    label: 'Admin',
    home: ROLE_HOME.ADMIN,
    dashboardEndpoint: '/dashboard/admin',
    nav: [
      { label: 'Insights', href: '/admin', icon: IconChartBar },
      { label: 'Live queue', href: '/admin/queue', icon: IconTicket },
      { label: 'Billing', href: '/admin/billing', icon: IconCash },
      { label: 'Test catalog', href: '/admin/lab-tests', icon: IconFlask },
      { label: 'Staff', href: '/admin/staff', icon: IconUsers, phase: 9 },
      { label: 'Inventory', href: '/admin/inventory', icon: IconPackage, phase: 9 },
      { label: 'Access log', href: '/admin/access-log', icon: IconShieldLock },
    ],
  },
};

export function roleConfig(role) {
  return ROLES[role] ?? null;
}
