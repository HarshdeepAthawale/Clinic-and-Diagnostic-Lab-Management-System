import { describe, expect, it } from 'vitest';
import { landingPath, ROLE_HOME, roleForPath, unverifiedRoleFromToken } from './roleRoutes';

function fakeJwt(claims) {
  const encode = (value) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256' })}.${encode(claims)}.signature`;
}

describe('roleForPath', () => {
  it('maps every role home and its sub-paths to the role', () => {
    for (const [role, home] of Object.entries(ROLE_HOME)) {
      expect(roleForPath(home)).toBe(role);
      expect(roleForPath(`${home}/anything`)).toBe(role);
    }
  });

  it('does not match look-alike prefixes or public pages', () => {
    expect(roleForPath('/labs')).toBeNull();
    expect(roleForPath('/administrator')).toBeNull();
    expect(roleForPath('/login')).toBeNull();
  });
});

describe('unverifiedRoleFromToken', () => {
  const now = Date.parse('2026-09-27T10:00:00Z');

  it('reads the role from a valid, unexpired token', () => {
    const token = fakeJwt({ sub: 'u1', role: 'PATHOLOGIST', exp: now / 1000 + 60 });
    expect(unverifiedRoleFromToken(token, now)).toBe('PATHOLOGIST');
  });

  it('rejects expired, unknown-role, malformed and missing tokens', () => {
    expect(unverifiedRoleFromToken(fakeJwt({ role: 'DOCTOR', exp: now / 1000 - 1 }), now)).toBeNull();
    expect(unverifiedRoleFromToken(fakeJwt({ role: 'SUPERUSER', exp: now / 1000 + 60 }), now)).toBeNull();
    expect(unverifiedRoleFromToken('garbage', now)).toBeNull();
    expect(unverifiedRoleFromToken(undefined, now)).toBeNull();
  });
});

describe('landingPath', () => {
  it('honours a requested page inside the user’s own area', () => {
    expect(landingPath('DOCTOR', '/doctor/patients')).toBe('/doctor/patients');
  });

  it('ignores requested pages in another role’s area or off-site', () => {
    expect(landingPath('DOCTOR', '/admin')).toBe('/doctor');
    expect(landingPath('DOCTOR', '//evil.example/doctor')).toBe('/doctor');
    expect(landingPath('DOCTOR', 'https://evil.example')).toBe('/doctor');
    expect(landingPath('PATIENT', null)).toBe('/patient');
  });
});
