export function greeting(date = new Date()) {
  const hour = date.getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

/** "Dr. Kabir Mehta" → "Dr. Kabir"; "Asha Rao" → "Asha". */
export function shortName(name = '') {
  const parts = name.trim().split(/\s+/);
  if (/^dr\.?$/i.test(parts[0]) && parts[1]) return `${parts[0]} ${parts[1]}`;
  return parts[0] ?? '';
}

export function longDate(date = new Date()) {
  return date.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' });
}
