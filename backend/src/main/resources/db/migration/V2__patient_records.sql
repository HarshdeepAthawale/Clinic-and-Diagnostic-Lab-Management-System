-- Phase 02: patient records (EMR), front-desk registration codes, care relationships, access log.
-- See Docs/Schema.md §1, §2, §6 and ADR-015 (doctor access), ADR-018 (registration codes).

-- ---------------------------------------------------------------------------------------------
-- Patient record: human-readable ID, clinical fields, emergency contact, registration code
-- ---------------------------------------------------------------------------------------------

CREATE SEQUENCE patient_code_seq START 1;

ALTER TABLE patients
    ADD COLUMN patient_code varchar(12) NOT NULL UNIQUE
        DEFAULT ('PID-' || lpad(nextval('patient_code_seq')::text, 6, '0')),
    ADD COLUMN blood_group varchar(3)
        CHECK (blood_group IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')),
    ADD COLUMN medical_history text,
    ADD COLUMN emergency_contact_name varchar(200),
    ADD COLUMN emergency_contact_phone varchar(20),
    -- Digits of the phone number, so search matches "98765 43210" and "+91-98765-43210" alike.
    ADD COLUMN phone_digits varchar(20) GENERATED ALWAYS AS (regexp_replace(phone, '\D', '', 'g')) STORED,
    -- One-time code printed at the front desk so the patient can link their own login (ADR-018).
    -- Only a SHA-256 hash is stored; cleared once used.
    ADD COLUMN claim_code_hash varchar(64) UNIQUE,
    ADD COLUMN claim_code_expires_at timestamptz,
    ADD COLUMN registered_by_user_id uuid REFERENCES users (id),
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX patients_full_name_lower_idx ON patients (lower(full_name));
CREATE INDEX patients_phone_digits_idx ON patients (phone_digits);

-- ---------------------------------------------------------------------------------------------
-- Appointments (Docs/Schema.md §2). Created now because doctor access to a record depends on
-- them (ADR-015); booking flows arrive in Phase 03.
-- ---------------------------------------------------------------------------------------------

CREATE TABLE appointments (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id   uuid        NOT NULL REFERENCES patients (id),
    doctor_id    uuid        NOT NULL REFERENCES doctors (id),
    scheduled_at timestamptz NOT NULL,
    queue_token  varchar(10),
    status       varchar(20) NOT NULL DEFAULT 'BOOKED'
        CHECK (status IN ('BOOKED', 'CHECKED_IN', 'IN_CONSULTATION', 'COMPLETED', 'NO_SHOW', 'CANCELLED')),
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX appointments_doctor_patient_idx ON appointments (doctor_id, patient_id);
CREATE INDEX appointments_doctor_time_idx ON appointments (doctor_id, scheduled_at);

-- ---------------------------------------------------------------------------------------------
-- Record access log (Docs/Schema.md §6, Security.md §6): append-only
-- ---------------------------------------------------------------------------------------------

CREATE TABLE patient_access_log (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id  uuid        NOT NULL REFERENCES patients (id),
    user_id     uuid        NOT NULL REFERENCES users (id),
    resource    varchar(20) NOT NULL
        CHECK (resource IN ('EMR', 'CONSULTATIONS', 'PRESCRIPTION', 'LAB_REPORT', 'LAB_HISTORY')),
    resource_id uuid,
    accessed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX patient_access_log_patient_idx ON patient_access_log (patient_id, accessed_at DESC);
CREATE INDEX patient_access_log_user_idx ON patient_access_log (user_id, accessed_at DESC);
CREATE INDEX patient_access_log_time_idx ON patient_access_log (accessed_at DESC);

-- No application path may rewrite history, including admins (Security.md §4).
CREATE FUNCTION forbid_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER patient_access_log_append_only
    BEFORE UPDATE OR DELETE ON patient_access_log
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();
