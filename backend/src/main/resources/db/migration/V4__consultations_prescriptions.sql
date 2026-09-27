-- Phase 04: consultations and e-prescriptions. See Docs/Schema.md §2, Docs/Rules.md §1b and ADR-021.

-- ---------------------------------------------------------------------------------------------
-- Doctor details printed on prescriptions (medical council registration is required on an Rx).
-- ---------------------------------------------------------------------------------------------

ALTER TABLE doctors
    ADD COLUMN qualification varchar(120),
    ADD COLUMN registration_number varchar(60);

-- ---------------------------------------------------------------------------------------------
-- Consultation: one per appointment, opened when the doctor starts writing, locked once finished.
-- ---------------------------------------------------------------------------------------------

CREATE TABLE consultations (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id    uuid         NOT NULL UNIQUE REFERENCES appointments (id),
    patient_id        uuid         NOT NULL REFERENCES patients (id),
    doctor_id         uuid         NOT NULL REFERENCES doctors (id),
    chief_complaint   varchar(500),
    notes             text,
    diagnosis         varchar(500),
    advice            text,
    follow_up_date    date,
    -- Vitals: all optional, sanity-bounded so a typo can't be stored as a real reading.
    bp_systolic       smallint     CHECK (bp_systolic BETWEEN 50 AND 300),
    bp_diastolic      smallint     CHECK (bp_diastolic BETWEEN 20 AND 200),
    pulse             smallint     CHECK (pulse BETWEEN 20 AND 250),
    temperature_c     numeric(4,1) CHECK (temperature_c BETWEEN 30 AND 45),
    weight_kg         numeric(5,1) CHECK (weight_kg BETWEEN 0.5 AND 400),
    spo2              smallint     CHECK (spo2 BETWEEN 50 AND 100),
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    completed_at      timestamptz,
    -- A finished consultation always has a diagnosis.
    CONSTRAINT consultations_completed_has_diagnosis CHECK (completed_at IS NULL OR diagnosis IS NOT NULL)
);

CREATE INDEX consultations_patient_idx ON consultations (patient_id, created_at DESC);
CREATE INDEX consultations_doctor_idx ON consultations (doctor_id, created_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Prescriptions: immutable once issued. A correction is a new prescription that replaces the old
-- one (replaces_id); nothing is ever edited or deleted.
-- ---------------------------------------------------------------------------------------------

CREATE SEQUENCE prescription_code_seq START 1;

CREATE TABLE prescriptions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    code            varchar(12) NOT NULL UNIQUE DEFAULT ('RX-' || lpad(nextval('prescription_code_seq')::text, 6, '0')),
    consultation_id uuid        NOT NULL REFERENCES consultations (id),
    patient_id      uuid        NOT NULL REFERENCES patients (id),
    doctor_id       uuid        NOT NULL REFERENCES doctors (id),
    advice          text,
    replaces_id     uuid        UNIQUE REFERENCES prescriptions (id),
    revision_reason varchar(300),
    issued_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT prescriptions_revision_has_reason CHECK (replaces_id IS NULL OR revision_reason IS NOT NULL)
);

CREATE INDEX prescriptions_patient_idx ON prescriptions (patient_id, issued_at DESC);
CREATE INDEX prescriptions_consultation_idx ON prescriptions (consultation_id);

CREATE TABLE prescription_items (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id uuid         NOT NULL REFERENCES prescriptions (id),
    position        smallint     NOT NULL CHECK (position >= 1),
    medicine        varchar(200) NOT NULL,
    dose            varchar(100),
    frequency       varchar(60)  NOT NULL,
    duration_days   smallint     CHECK (duration_days BETWEEN 1 AND 365),
    instructions    varchar(300),
    UNIQUE (prescription_id, position)
);

CREATE INDEX prescription_items_medicine_idx ON prescription_items (lower(medicine));

CREATE TRIGGER prescriptions_append_only
    BEFORE UPDATE OR DELETE ON prescriptions
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();

CREATE TRIGGER prescription_items_append_only
    BEFORE UPDATE OR DELETE ON prescription_items
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();
