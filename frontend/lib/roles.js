import {
  IconAlertTriangle,
  IconBuildingHospital,
  IconCalendarEvent,
  IconCash,
  IconChartBar,
  IconClipboardCheck,
  IconFileText,
  IconFlask,
  IconHeartbeat,
  IconHistory,
  IconHome,
  IconId,
  IconListCheck,
  IconMicroscope,
  IconPackage,
  IconPill,
  IconQrcode,
  IconReceipt,
  IconReportMedical,
  IconShieldLock,
  IconStethoscope,
  IconTestPipe,
  IconTicket,
  IconTimeline,
  IconUserPlus,
  IconUsers,
} from '@tabler/icons-react';
import { ROLE_HOME } from './roleRoutes';

/**
 * Per-role workspace config (Docs/Design.md §3, §6).
 * - `nav`: sidebar/tab items. `phase` marks items not built yet (shown disabled with a "Soon" tag).
 * - `upcoming`: what this workspace will do, shown on the Phase 01 dashboard.
 */
export const ROLES = {
  PATIENT: {
    key: 'PATIENT',
    label: 'Patient',
    home: ROLE_HOME.PATIENT,
    dashboardEndpoint: '/dashboard/patient',
    accentVar: 'var(--role-patient)',
    nav: [
      { label: 'Home', href: '/patient', icon: IconHome },
      { label: 'Appointments', href: '/patient/appointments', icon: IconCalendarEvent, phase: 3 },
      { label: 'Reports', href: '/patient/reports', icon: IconFileText, phase: 8 },
      { label: 'Bills', href: '/patient/bills', icon: IconReceipt, phase: 6 },
      { label: 'Profile', href: '/patient/profile', icon: IconId, phase: 2 },
    ],
    upcoming: [
      { icon: IconTimeline, title: 'Live sample tracking', text: 'Follow every test from collection to verified report, like tracking a parcel.', phase: 7 },
      { icon: IconCalendarEvent, title: 'Book in seconds', text: 'Pick a doctor, see open slots, get prep instructions and reminders.', phase: 3 },
      { icon: IconFileText, title: 'Readable reports', text: 'Results with clear normal ranges, plus a signed PDF to download.', phase: 8 },
    ],
  },
  DOCTOR: {
    key: 'DOCTOR',
    label: 'Doctor',
    home: ROLE_HOME.DOCTOR,
    dashboardEndpoint: '/dashboard/doctor',
    accentVar: 'var(--role-doctor)',
    nav: [
      { label: 'Today', href: '/doctor', icon: IconHome },
      { label: 'Schedule', href: '/doctor/schedule', icon: IconCalendarEvent, phase: 3 },
      { label: 'Patients', href: '/doctor/patients', icon: IconUsers, phase: 2 },
      { label: 'Consultations', href: '/doctor/consultations', icon: IconStethoscope, phase: 4 },
      { label: 'Lab reports', href: '/doctor/reports', icon: IconReportMedical, phase: 8 },
    ],
    upcoming: [
      { icon: IconStethoscope, title: 'Consult workspace', text: 'Notes, diagnosis, prescriptions and test orders in one split view, allergies always pinned.', phase: 4 },
      { icon: IconPill, title: 'Fast prescriptions', text: 'Keyboard-first prescription rows that export to a signed PDF.', phase: 4 },
      { icon: IconFlask, title: 'One-click test orders', text: 'Order lab tests from the consult — the lab sees them instantly.', phase: 5 },
    ],
  },
  PATHOLOGIST: {
    key: 'PATHOLOGIST',
    label: 'Pathologist',
    home: ROLE_HOME.PATHOLOGIST,
    dashboardEndpoint: '/dashboard/pathologist',
    accentVar: 'var(--role-pathologist)',
    nav: [
      { label: 'Today', href: '/pathology', icon: IconHome },
      { label: 'Verification queue', href: '/pathology/queue', icon: IconListCheck, phase: 8 },
      { label: 'My verifications', href: '/pathology/history', icon: IconHistory, phase: 8 },
      { label: 'Profile', href: '/pathology/profile', icon: IconId, phase: 8 },
    ],
    upcoming: [
      { icon: IconMicroscope, title: 'Focus mode', text: 'One result at a time with range bar, patient trend and keyboard sign-off (V / R / J / K).', phase: 8 },
      { icon: IconAlertTriangle, title: 'Critical values first', text: 'Out-of-range and critical results flagged at the top of the queue.', phase: 8 },
      { icon: IconClipboardCheck, title: 'Return for retest', text: 'Send doubtful results back to the bench with a reason — history kept.', phase: 8 },
    ],
  },
  RECEPTIONIST: {
    key: 'RECEPTIONIST',
    label: 'Receptionist',
    home: ROLE_HOME.RECEPTIONIST,
    dashboardEndpoint: '/dashboard/receptionist',
    accentVar: 'var(--role-receptionist)',
    nav: [
      { label: 'Today', href: '/reception', icon: IconHome },
      { label: 'Live queue', href: '/reception/queue', icon: IconTicket, phase: 3 },
      { label: 'Register patient', href: '/reception/register', icon: IconUserPlus, phase: 2 },
      { label: 'Appointments', href: '/reception/appointments', icon: IconCalendarEvent, phase: 3 },
      { label: 'Billing', href: '/reception/billing', icon: IconCash, phase: 6 },
    ],
    upcoming: [
      { icon: IconTicket, title: 'Live queue board', text: 'Waiting → With doctor → Done, per doctor. Issue a walk-in token with one key.', phase: 3 },
      { icon: IconUserPlus, title: 'Register once', text: 'One patient record shared by the clinic and the lab — no re-typing.', phase: 2 },
      { icon: IconAlertTriangle, title: 'Rejection inbox', text: 'Get told the moment a sample needs a redraw, with a one-click rebook.', phase: 7 },
    ],
  },
  LAB_TECHNICIAN: {
    key: 'LAB_TECHNICIAN',
    label: 'Lab Technician',
    home: ROLE_HOME.LAB_TECHNICIAN,
    dashboardEndpoint: '/dashboard/lab-technician',
    accentVar: 'var(--role-lab-technician)',
    nav: [
      { label: 'Bench', href: '/lab', icon: IconHome },
      { label: 'Orders', href: '/lab/orders', icon: IconFlask, phase: 5 },
      { label: 'Samples', href: '/lab/samples', icon: IconTestPipe, phase: 7 },
      { label: 'Inventory', href: '/lab/inventory', icon: IconPackage, phase: 9 },
    ],
    upcoming: [
      { icon: IconQrcode, title: 'Scan-first bench mode', text: 'Scan or type a sample code and jump straight to its next step. Built for gloved hands.', phase: 7 },
      { icon: IconTestPipe, title: 'Tube-cap colors', text: 'Real vacutainer colors with automatic tube-type mismatch warnings.', phase: 7 },
      { icon: IconHeartbeat, title: 'Result entry with ranges', text: 'See where a value sits against its reference range as you type.', phase: 8 },
    ],
  },
  ADMIN: {
    key: 'ADMIN',
    label: 'Admin',
    home: ROLE_HOME.ADMIN,
    dashboardEndpoint: '/dashboard/admin',
    accentVar: 'var(--role-admin)',
    nav: [
      { label: 'Insights', href: '/admin', icon: IconChartBar },
      { label: 'Staff', href: '/admin/staff', icon: IconUsers, phase: 9 },
      { label: 'Inventory', href: '/admin/inventory', icon: IconPackage, phase: 9 },
      { label: 'Access log', href: '/admin/access-log', icon: IconShieldLock, phase: 2 },
    ],
    upcoming: [
      { icon: IconChartBar, title: 'Insight dashboard', text: 'Patients, revenue, samples in flight and turnaround time — live.', phase: 9 },
      { icon: IconBuildingHospital, title: 'TAT heatmap', text: 'See which tests run slow, on which days, at a glance.', phase: 9 },
      { icon: IconShieldLock, title: 'Record access log', text: 'Who opened which patient record, and when.', phase: 2 },
    ],
  },
};

export function roleConfig(role) {
  return ROLES[role] ?? null;
}
