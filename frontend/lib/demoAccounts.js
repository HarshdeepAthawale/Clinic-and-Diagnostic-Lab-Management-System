/**
 * Demo accounts from backend/src/main/resources/db/seed/R__dev_seed.sql.
 * Only shown on the login page in local development (NODE_ENV === 'development');
 * the seed itself only loads with the backend's "dev" profile.
 */
export const DEMO_PASSWORD = 'Demo@12345';

export const DEMO_ACCOUNTS = [
  { role: 'PATIENT', email: 'patient@demo.cdlms.dev' },
  { role: 'DOCTOR', email: 'doctor@demo.cdlms.dev' },
  { role: 'PATHOLOGIST', email: 'pathologist@demo.cdlms.dev' },
  { role: 'RECEPTIONIST', email: 'reception@demo.cdlms.dev' },
  { role: 'LAB_TECHNICIAN', email: 'lab@demo.cdlms.dev' },
  { role: 'ADMIN', email: 'admin@demo.cdlms.dev' },
];
