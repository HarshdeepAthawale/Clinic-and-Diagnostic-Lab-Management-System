-- Dev/demo seed data. Loaded ONLY with the "dev" profile (see application-dev.yml); never in production.
-- Repeatable migration: re-applied when this file changes; inserts are idempotent.
--
-- Demo accounts — all use the password: Demo@12345
--   patient@demo.cdlms.dev        Patient
--   doctor@demo.cdlms.dev         Doctor
--   pathologist@demo.cdlms.dev    Pathologist
--   reception@demo.cdlms.dev      Receptionist
--   lab@demo.cdlms.dev            Lab Technician
--   admin@demo.cdlms.dev          Admin

INSERT INTO users (id, email, password_hash, role) VALUES
    ('00000000-0000-4000-8000-000000000001', 'patient@demo.cdlms.dev',     '$2a$10$bJNIt9Z3Julm3zPeOD3hYuob.FUDjAcALLW.C3q.L5B7dxObxbY.m', 'PATIENT'),
    ('00000000-0000-4000-8000-000000000002', 'doctor@demo.cdlms.dev',      '$2a$10$bJNIt9Z3Julm3zPeOD3hYuob.FUDjAcALLW.C3q.L5B7dxObxbY.m', 'DOCTOR'),
    ('00000000-0000-4000-8000-000000000003', 'pathologist@demo.cdlms.dev', '$2a$10$bJNIt9Z3Julm3zPeOD3hYuob.FUDjAcALLW.C3q.L5B7dxObxbY.m', 'PATHOLOGIST'),
    ('00000000-0000-4000-8000-000000000004', 'reception@demo.cdlms.dev',   '$2a$10$bJNIt9Z3Julm3zPeOD3hYuob.FUDjAcALLW.C3q.L5B7dxObxbY.m', 'RECEPTIONIST'),
    ('00000000-0000-4000-8000-000000000005', 'lab@demo.cdlms.dev',         '$2a$10$bJNIt9Z3Julm3zPeOD3hYuob.FUDjAcALLW.C3q.L5B7dxObxbY.m', 'LAB_TECHNICIAN'),
    ('00000000-0000-4000-8000-000000000006', 'admin@demo.cdlms.dev',       '$2a$10$bJNIt9Z3Julm3zPeOD3hYuob.FUDjAcALLW.C3q.L5B7dxObxbY.m', 'ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO patients (user_id, full_name, dob, gender, phone, address, known_allergies) VALUES
    ('00000000-0000-4000-8000-000000000001', 'Asha Rao', '1994-05-12', 'FEMALE', '+91 98765 43210',
     '12 MG Road, Pune', 'Penicillin')
ON CONFLICT DO NOTHING;

INSERT INTO doctors (user_id, full_name, specialization) VALUES
    ('00000000-0000-4000-8000-000000000002', 'Dr. Kabir Mehta', 'General Medicine')
ON CONFLICT DO NOTHING;

INSERT INTO pathologists (user_id, full_name, qualification, registration_number) VALUES
    ('00000000-0000-4000-8000-000000000003', 'Dr. Meera Iyer', 'MD Pathology', 'MMC-2016-04821')
ON CONFLICT DO NOTHING;

INSERT INTO staff (user_id, full_name, staff_type) VALUES
    ('00000000-0000-4000-8000-000000000004', 'Rohan Das', 'RECEPTIONIST'),
    ('00000000-0000-4000-8000-000000000005', 'Priya Nair', 'LAB_TECHNICIAN'),
    ('00000000-0000-4000-8000-000000000006', 'Anil Kapoor', 'ADMIN')
ON CONFLICT DO NOTHING;

-- One appointment so the demo doctor has a care relationship with the demo patient (ADR-015).
INSERT INTO appointments (patient_id, doctor_id, scheduled_at, status)
SELECT p.id, d.id, date_trunc('hour', now()) + interval '1 hour', 'BOOKED'
FROM patients p, doctors d
WHERE p.user_id = '00000000-0000-4000-8000-000000000001'
  AND d.user_id = '00000000-0000-4000-8000-000000000002'
  AND NOT EXISTS (SELECT 1 FROM appointments a WHERE a.patient_id = p.id AND a.doctor_id = d.id);
