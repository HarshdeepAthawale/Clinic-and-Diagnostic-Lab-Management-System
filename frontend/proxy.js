import { NextResponse } from 'next/server';
import { AUTH_COOKIE, ROLE_HOME, roleForPath, unverifiedRoleFromToken } from '@/lib/roleRoutes';

/**
 * Redirect convenience only (Next.js 16 "proxy", formerly middleware). Sends logged-out users to
 * /login and users to their own role's area. NOT a security boundary — the backend checks every
 * request (Docs/Security.md §2).
 */
export function proxy(request) {
  const { pathname, search } = request.nextUrl;
  const role = unverifiedRoleFromToken(request.cookies.get(AUTH_COOKIE)?.value);
  const redirect = (path) => NextResponse.redirect(new URL(path, request.url));

  if (pathname === '/') return redirect(role ? ROLE_HOME[role] : '/login');

  if (pathname === '/login' || pathname === '/register') {
    return role ? redirect(ROLE_HOME[role]) : NextResponse.next();
  }

  const areaRole = roleForPath(pathname);
  if (areaRole && !role) {
    return redirect(`/login?next=${encodeURIComponent(pathname + search)}`);
  }
  if (areaRole && role !== areaRole) return redirect(ROLE_HOME[role]);

  return NextResponse.next();
}

export const config = {
  matcher: [
    '/',
    '/login',
    '/register',
    '/patient/:path*',
    '/doctor/:path*',
    '/pathology/:path*',
    '/reception/:path*',
    '/lab/:path*',
    '/admin/:path*',
  ],
};
