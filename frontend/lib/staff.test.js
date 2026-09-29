import { describe, expect, it } from 'vitest';
import { createBody, passwordProblem, staffProblems } from './staff';

const base = { fullName: 'Ravi Front', email: 'ravi@clinic.test', specialization: '', qualification: '', registrationNumber: '' };

describe('staffProblems', () => {
  it('needs a name and a real-looking email for everyone', () => {
    expect(staffProblems({ ...base, role: 'RECEPTIONIST' })).toEqual({});
    expect(staffProblems({ ...base, role: 'RECEPTIONIST', fullName: '  ' })).toEqual({ fullName: 'Required' });
    expect(staffProblems({ ...base, role: 'ADMIN', email: 'nope' }).email).toMatch(/valid email/);
  });

  it('asks a doctor for a specialization', () => {
    expect(staffProblems({ ...base, role: 'DOCTOR' }).specialization).toBeTruthy();
    expect(staffProblems({ ...base, role: 'DOCTOR', specialization: 'Cardiology' })).toEqual({});
  });

  it('asks a pathologist for a qualification and a registration number', () => {
    const problems = staffProblems({ ...base, role: 'PATHOLOGIST' });
    expect(Object.keys(problems).sort()).toEqual(['qualification', 'registrationNumber']);
    expect(staffProblems({ ...base, role: 'PATHOLOGIST', qualification: 'MD', registrationNumber: 'MMC-1' })).toEqual({});
  });
});

describe('createBody', () => {
  it('sends only what the role uses, trimmed', () => {
    expect(createBody({ ...base, role: 'RECEPTIONIST', specialization: 'ignored', fullName: ' Ravi Front ' })).toEqual({
      role: 'RECEPTIONIST', fullName: 'Ravi Front', email: 'ravi@clinic.test',
    });
    expect(createBody({ ...base, role: 'DOCTOR', specialization: ' Cardiology ' }).specialization).toBe('Cardiology');
    expect(createBody({ ...base, role: 'PATHOLOGIST', qualification: 'MD', registrationNumber: ' MMC-1 ' })).toMatchObject({
      qualification: 'MD', registrationNumber: 'MMC-1',
    });
  });
});

describe('passwordProblem', () => {
  const ok = { current: 'old-password', next: 'a-new-password', confirm: 'a-new-password' };

  it('accepts a proper change', () => {
    expect(passwordProblem(ok)).toBeNull();
  });

  it('checks each field in the order a person fills them', () => {
    expect(passwordProblem({ ...ok, current: '' }).field).toBe('current');
    expect(passwordProblem({ ...ok, next: 'short', confirm: 'short' }).field).toBe('next');
    expect(passwordProblem({ ...ok, next: 'x'.repeat(73), confirm: 'x'.repeat(73) }).field).toBe('next');
    expect(passwordProblem({ ...ok, next: 'old-password', confirm: 'old-password' }).field).toBe('next');
    expect(passwordProblem({ ...ok, confirm: 'different-one' }).field).toBe('confirm');
  });
});
