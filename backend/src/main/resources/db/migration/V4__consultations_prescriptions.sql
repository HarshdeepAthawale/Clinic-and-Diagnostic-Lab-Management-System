-- Phase 04: consultations and e-prescriptions. See Docs/Schema.md §2 and ADR-021.
-- A consultation is the doctor's record of one appointment. It stays a DRAFT while the patient
-- is in the room and becomes read-only once COMPLETED. The prescription issued with it is
-- numbered (RX-000001) and read-only once issued. Both rules are enforced here, not only in Java.

-- ---------------------------------------------------------------------------------------------
-- Doctor details printed on every prescription
-- ---------------------------------------------------------------------------------------------

ALTER TABLE doctors
    ADD COLUMN qualification varchar(120),
    ADD COLUMN registration_number varchar(60) UNIQUE;

-- ---------------------------------------------------------------------------------------------
-- Consultations: one per appointment
-- ---------------------------------------------------------------------------------------------

CREATE TABLE consultations (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id    uuid         NOT NULL UNIQUE REFERENCES appointments (id),
    doctor_id         uuid         NOT NULL REFERENCES doctors (id),
    patient_id        uuid         NOT NULL REFERENCES patients (id),
    status            varchar(10)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'COMPLETED')),
    chief_complaint   varchar(500),
    notes             text,
    diagnosis         varchar(1000),
    advice            text,
    follow_up_date    date,
    -- Vitals, all optional
    bp_systolic       smallint     CHECK (bp_systolic BETWEEN 40 AND 300),
    bp_diastolic      smallint     CHECK (bp_diastolic BETWEEN 20 AND 200),
    pulse_bpm         smallint     CHECK (pulse_bpm BETWEEN 20 AND 250),
    temperature_c     numeric(4,1) CHECK (temperature_c BETWEEN 30 AND 45),
    spo2_percent      smallint     CHECK (spo2_percent BETWEEN 50 AND 100),
    weight_kg         numeric(5,1) CHECK (weight_kg BETWEEN 0.5 AND 400),
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    completed_at      timestamptz,
    CONSTRAINT consultations_completed_has_diagnosis
        CHECK (status = 'DRAFT' OR (diagnosis IS NOT NULL AND completed_at IS NOT NULL))
);

CREATE INDEX consultations_patient_idx ON consultations (patient_id, created_at DESC);
CREATE INDEX consultations_doctor_idx ON consultations (doctor_id, created_at DESC);

-- A completed consultation is part of the medical record: no edits, no deletes.
CREATE FUNCTION lock_completed_consultation() RETURNS trigger AS $$
BEGIN
    IF OLD.status = 'COMPLETED' THEN
        RAISE EXCEPTION 'consultation % is completed and cannot be changed', OLD.id;
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER consultations_lock_completed
    BEFORE UPDATE OR DELETE ON consultations
    FOR EACH ROW EXECUTE FUNCTION lock_completed_consultation();

-- ---------------------------------------------------------------------------------------------
-- Prescriptions: at most one per consultation, with ordered medicine lines
-- ---------------------------------------------------------------------------------------------

CREATE SEQUENCE prescription_code_seq START 1;

CREATE TABLE prescriptions (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id   uuid        NOT NULL UNIQUE REFERENCES consultations (id),
    -- Assigned when issued (consultation completed); NULL while still a draft.
    prescription_code varchar(12) UNIQUE,
    issued_at         timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT prescriptions_issued_complete CHECK ((prescription_code IS NULL) = (issued_at IS NULL))
);

CREATE TABLE prescription_items (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id uuid         NOT NULL REFERENCES prescriptions (id) ON DELETE CASCADE,
    position        smallint     NOT NULL CHECK (position >= 1),
    medicine        varchar(200) NOT NULL,
    dosage          varchar(100),
    frequency       varchar(100) NOT NULL,
    duration        varchar(60)  NOT NULL,
    instructions    varchar(300),
    UNIQUE (prescription_id, position)
);

CREATE FUNCTION lock_issued_prescription() RETURNS trigger AS $$
BEGIN
    IF OLD.issued_at IS NOT NULL THEN
        RAISE EXCEPTION 'prescription % is issued and cannot be changed', OLD.id;
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prescriptions_lock_issued
    BEFORE UPDATE OR DELETE ON prescriptions
    FOR EACH ROW EXECUTE FUNCTION lock_issued_prescription();

CREATE FUNCTION lock_items_of_issued_prescription() RETURNS trigger AS $$
DECLARE
    issued timestamptz;
BEGIN
    SELECT issued_at INTO issued FROM prescriptions
    WHERE id = CASE WHEN TG_OP = 'INSERT' THEN NEW.prescription_id ELSE OLD.prescription_id END;
    IF issued IS NOT NULL THEN
        RAISE EXCEPTION 'prescription is issued; its medicines cannot be changed';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prescription_items_lock_issued
    BEFORE INSERT OR UPDATE OR DELETE ON prescription_items
    FOR EACH ROW EXECUTE FUNCTION lock_items_of_issued_prescription();

-- ---------------------------------------------------------------------------------------------
-- Formulary: reference list of common generic medicines for the prescription builder's
-- autocomplete. Doctors can still type any medicine.
-- ---------------------------------------------------------------------------------------------

CREATE TABLE formulary (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             varchar(200) NOT NULL,
    form             varchar(30)  NOT NULL,
    default_strength varchar(60),
    UNIQUE (name, form)
);

CREATE INDEX formulary_name_lower_idx ON formulary (lower(name));

INSERT INTO formulary (name, form, default_strength) VALUES
    ('Paracetamol', 'Tablet', '500 mg'),
    ('Paracetamol', 'Syrup', '120 mg/5 mL'),
    ('Ibuprofen', 'Tablet', '400 mg'),
    ('Diclofenac', 'Tablet', '50 mg'),
    ('Aceclofenac', 'Tablet', '100 mg'),
    ('Amoxicillin', 'Capsule', '500 mg'),
    ('Amoxicillin + Clavulanic acid', 'Tablet', '625 mg'),
    ('Azithromycin', 'Tablet', '500 mg'),
    ('Cefixime', 'Tablet', '200 mg'),
    ('Ciprofloxacin', 'Tablet', '500 mg'),
    ('Doxycycline', 'Capsule', '100 mg'),
    ('Metronidazole', 'Tablet', '400 mg'),
    ('Nitrofurantoin', 'Capsule', '100 mg'),
    ('Pantoprazole', 'Tablet', '40 mg'),
    ('Omeprazole', 'Capsule', '20 mg'),
    ('Ranitidine', 'Tablet', '150 mg'),
    ('Domperidone', 'Tablet', '10 mg'),
    ('Ondansetron', 'Tablet', '4 mg'),
    ('Oral rehydration salts', 'Sachet', NULL),
    ('Loperamide', 'Capsule', '2 mg'),
    ('Cetirizine', 'Tablet', '10 mg'),
    ('Levocetirizine', 'Tablet', '5 mg'),
    ('Montelukast', 'Tablet', '10 mg'),
    ('Salbutamol', 'Inhaler', '100 mcg/puff'),
    ('Budesonide', 'Inhaler', '200 mcg/puff'),
    ('Dextromethorphan', 'Syrup', '10 mg/5 mL'),
    ('Metformin', 'Tablet', '500 mg'),
    ('Glimepiride', 'Tablet', '1 mg'),
    ('Sitagliptin', 'Tablet', '100 mg'),
    ('Insulin glargine', 'Injection', '100 IU/mL'),
    ('Amlodipine', 'Tablet', '5 mg'),
    ('Telmisartan', 'Tablet', '40 mg'),
    ('Losartan', 'Tablet', '50 mg'),
    ('Metoprolol', 'Tablet', '25 mg'),
    ('Atorvastatin', 'Tablet', '10 mg'),
    ('Rosuvastatin', 'Tablet', '10 mg'),
    ('Aspirin', 'Tablet', '75 mg'),
    ('Clopidogrel', 'Tablet', '75 mg'),
    ('Levothyroxine', 'Tablet', '50 mcg'),
    ('Prednisolone', 'Tablet', '10 mg'),
    ('Vitamin D3', 'Capsule', '60000 IU'),
    ('Calcium + Vitamin D3', 'Tablet', '500 mg'),
    ('Ferrous sulfate', 'Tablet', '200 mg'),
    ('Folic acid', 'Tablet', '5 mg'),
    ('Vitamin B complex', 'Tablet', NULL),
    ('Fluconazole', 'Tablet', '150 mg'),
    ('Clotrimazole', 'Cream', '1%'),
    ('Mupirocin', 'Ointment', '2%'),
    ('Albendazole', 'Tablet', '400 mg'),
    ('Ivermectin', 'Tablet', '12 mg')
ON CONFLICT (name, form) DO NOTHING;
