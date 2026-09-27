/** Display formatting shared across screens (Design.md §7). Clinic time zone: Asia/Kolkata. */

export const CLINIC_TIME_ZONE = 'Asia/Kolkata';

export function formatTime(iso) {
  return new Date(iso).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', timeZone: CLINIC_TIME_ZONE });
}

export function formatDate(iso) {
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: CLINIC_TIME_ZONE });
}

export function formatDateTime(iso) {
  return `${formatDate(iso)}, ${formatTime(iso)}`;
}

/** "just now", "12 min ago", "3 h ago", else the date. */
export function formatRelative(iso, now = Date.now()) {
  const minutes = Math.round((now - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  if (minutes < 24 * 60) return `${Math.round(minutes / 60)} h ago`;
  return formatDate(iso);
}

const GENDER = { MALE: 'M', FEMALE: 'F', OTHER: 'Other' };

/** "32 · F" */
export function ageGender(age, gender) {
  return `${age} · ${GENDER[gender] ?? gender}`;
}

export const APPOINTMENT_STATUS = {
  BOOKED: 'booked',
  CHECKED_IN: 'checked-in',
  IN_CONSULTATION: 'in-consult',
  COMPLETED: 'done',
  NO_SHOW: 'no-show',
  CANCELLED: 'rejected',
};

const RUPEES = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const RUPEES_PAISE = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });

/** "₹1,250" or "₹99.50": prices come from the API as decimal numbers or strings. */
export function formatMoney(amount) {
  const value = Number(amount ?? 0);
  return (Number.isInteger(value) ? RUPEES : RUPEES_PAISE).format(value);
}
