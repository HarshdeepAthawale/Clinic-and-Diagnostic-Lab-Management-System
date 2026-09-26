/**
 * Role → route mapping, kept free of UI imports so proxy.js can use it too.
 * Route prefixes follow Docs/TechSpecifications.md §3.
 */

export const AUTH_COOKIE = 'cdlms_token';

export const ROLE_HOME = {
  PATIENT: '/patient',
  DOCTOR: '/doctor',
  PATHOLOGIST: '/pathology',
  RECEPTIONIST: '/reception',
  LAB_TECHNICIAN: '/lab',
  ADMIN: '/admin',
};

/** Which role owns the area a path belongs to, or null for public/other paths. */
export function roleForPath(pathname) {
  for (const [role, home] of Object.entries(ROLE_HOME)) {
    if (pathname === home || pathname.startsWith(`${home}/`)) return role;
  }
  return null;
}

/**
 * Reads the role from the JWT payload WITHOUT verifying its signature. This is only used for
 * redirect convenience (UX); the backend verifies every request and is the security boundary.
 * Returns null for missing, malformed or expired tokens.
 */
export function unverifiedRoleFromToken(token, now = Date.now()) {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const claims = JSON.parse(json);
    if (typeof claims.exp === 'number' && claims.exp * 1000 <= now) return null;
    return claims.role in ROLE_HOME ? claims.role : null;
  } catch {
    return null;
  }
}

/** Where to go after login: the requested page if it's in the user's own area, else their home. */
export function landingPath(role, requested) {
  if (requested && requested.startsWith('/') && !requested.startsWith('//') && roleForPath(requested) === role) {
    return requested;
  }
  return ROLE_HOME[role] ?? '/login';
}
