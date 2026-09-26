import { Badge } from '@mantine/core';
import {
  IconAlertOctagon,
  IconAlertTriangle,
  IconCheck,
  IconCircleDashed,
  IconClock,
  IconFlask,
  IconLoader2,
  IconRefresh,
  IconStethoscope,
  IconX,
} from '@tabler/icons-react';

/**
 * One status vocabulary for the whole app (Design.md §4 "Status badge"): icon + label + semantic
 * color. Color is never the only signal.
 */
const STATUSES = {
  booked: { label: 'Booked', tone: 'info', icon: IconClock },
  waiting: { label: 'Waiting', tone: 'warning', icon: IconClock },
  'checked-in': { label: 'Checked in', tone: 'info', icon: IconCheck },
  'in-consult': { label: 'In consultation', tone: 'brand', icon: IconStethoscope },
  done: { label: 'Done', tone: 'success', icon: IconCheck },
  'no-show': { label: 'No-show', tone: 'muted', icon: IconX },
  pending: { label: 'Pending', tone: 'muted', icon: IconCircleDashed },
  'in-testing': { label: 'In testing', tone: 'info', icon: IconLoader2 },
  'to-collect': { label: 'To collect', tone: 'muted', icon: IconFlask },
  'to-receive': { label: 'To receive', tone: 'info', icon: IconFlask },
  retest: { label: 'Retest', tone: 'warning', icon: IconRefresh },
  rejected: { label: 'Rejected', tone: 'critical', icon: IconX },
  verified: { label: 'Verified', tone: 'success', icon: IconCheck },
  normal: { label: 'Normal', tone: 'success', icon: IconCheck },
  high: { label: 'High', tone: 'warning', icon: IconAlertTriangle },
  low: { label: 'Low', tone: 'warning', icon: IconAlertTriangle },
  critical: { label: 'Critical', tone: 'critical', icon: IconAlertOctagon },
  paid: { label: 'Paid', tone: 'success', icon: IconCheck },
  due: { label: 'Due', tone: 'warning', icon: IconClock },
  'low-stock': { label: 'Low stock', tone: 'warning', icon: IconAlertTriangle },
};

const TONES = {
  success: ['var(--success)', 'var(--success-soft)'],
  warning: ['var(--warning)', 'var(--warning-soft)'],
  critical: ['var(--critical)', 'var(--critical-soft)'],
  info: ['var(--info)', 'var(--info-soft)'],
  brand: ['var(--accent)', 'var(--accent-soft)'],
  muted: ['var(--text-muted)', 'var(--surface-2)'],
};

export function StatusBadge({ status, label, size = 'sm' }) {
  const config = STATUSES[status] ?? { label: status, tone: 'muted', icon: IconCircleDashed };
  const [color, background] = TONES[config.tone];
  const Icon = config.icon;
  return (
    <Badge
      size={size}
      radius="sm"
      leftSection={<Icon size={12} stroke={2.2} />}
      styles={{ root: { color, background, textTransform: 'none', fontWeight: 600, border: 'none' } }}
    >
      {label ?? config.label}
    </Badge>
  );
}
