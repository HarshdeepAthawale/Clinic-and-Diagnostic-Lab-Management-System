-- Phase 05: lab test catalog and ordering. See Docs/Schema.md §3 and ADR-022.
-- The catalog is reference data shipped with the schema (admins maintain it afterwards).
-- A lab order is created by a doctor — from a consultation or directly — with no re-typing:
-- patient, doctor and consultation come from the context, prices are copied at order time.

-- ---------------------------------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------------------------------

CREATE TABLE lab_tests (
    id                 uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    code               varchar(20)   NOT NULL UNIQUE,
    name               varchar(200)  NOT NULL,
    category           varchar(40)   NOT NULL,
    sample_type        varchar(20)   NOT NULL CHECK (sample_type IN ('BLOOD', 'URINE', 'STOOL', 'SWAB')),
    -- Vacutainer cap / container the sample must be collected in (Rules.md §2 tube-type rule)
    required_tube_type varchar(20)   NOT NULL
        CHECK (required_tube_type IN ('EDTA', 'PLAIN', 'SST', 'CITRATE', 'FLUORIDE', 'HEPARIN', 'URINE_CUP', 'STOOL_CUP', 'SWAB_TUBE')),
    price              numeric(10,2) NOT NULL CHECK (price >= 0),
    turnaround_hours   smallint      NOT NULL CHECK (turnaround_hours BETWEEN 1 AND 720),
    prep_instructions  varchar(500),
    is_active          boolean       NOT NULL DEFAULT true,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX lab_tests_name_lower_idx ON lab_tests (lower(name));

-- What a test measures, each with its own unit and reference range (Phase 08 checks results
-- against these). Qualitative parameters (e.g. "Dengue NS1: Negative") have no numeric range.
CREATE TABLE lab_test_parameters (
    id            uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_test_id   uuid          NOT NULL REFERENCES lab_tests (id) ON DELETE CASCADE,
    position      smallint      NOT NULL CHECK (position >= 1),
    name          varchar(120)  NOT NULL,
    unit          varchar(30),
    ref_low       numeric(12,3),
    ref_high      numeric(12,3),
    critical_low  numeric(12,3),
    critical_high numeric(12,3),
    UNIQUE (lab_test_id, position),
    CHECK (ref_low IS NULL OR ref_high IS NULL OR ref_low <= ref_high)
);

-- ---------------------------------------------------------------------------------------------
-- Orders
-- ---------------------------------------------------------------------------------------------

CREATE SEQUENCE lab_order_code_seq START 1;

CREATE TABLE lab_orders (
    id                   uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_code           varchar(12)  NOT NULL UNIQUE
        DEFAULT ('LO-' || lpad(nextval('lab_order_code_seq')::text, 6, '0')),
    patient_id           uuid         NOT NULL REFERENCES patients (id),
    ordering_doctor_id   uuid         NOT NULL REFERENCES doctors (id),
    -- Set when ordered during a consultation; one order per consultation.
    consultation_id      uuid         UNIQUE REFERENCES consultations (id),
    priority             varchar(10)  NOT NULL DEFAULT 'ROUTINE' CHECK (priority IN ('ROUTINE', 'URGENT')),
    clinical_notes       varchar(500),
    status               varchar(12)  NOT NULL DEFAULT 'ORDERED' CHECK (status IN ('ORDERED', 'CANCELLED')),
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    cancelled_at         timestamptz,
    cancelled_by_user_id uuid         REFERENCES users (id),
    cancellation_reason  varchar(300)
);

CREATE INDEX lab_orders_patient_idx ON lab_orders (patient_id, created_at DESC);
CREATE INDEX lab_orders_queue_idx ON lab_orders (status, priority, created_at) WHERE status = 'ORDERED';

CREATE TABLE lab_order_items (
    id                uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id      uuid          NOT NULL REFERENCES lab_orders (id),
    lab_test_id       uuid          NOT NULL REFERENCES lab_tests (id),
    -- Snapshot at order time, so later catalog changes never alter what was ordered or billed.
    test_name         varchar(200)  NOT NULL,
    price_at_order    numeric(10,2) NOT NULL CHECK (price_at_order >= 0),
    status            varchar(12)   NOT NULL DEFAULT 'ORDERED' CHECK (status IN ('ORDERED', 'CANCELLED')),
    created_at        timestamptz   NOT NULL DEFAULT now(),
    cancelled_at      timestamptz
);

-- The same test can't be live twice on one order.
CREATE UNIQUE INDEX lab_order_items_live_test_uq ON lab_order_items (lab_order_id, lab_test_id)
    WHERE status <> 'CANCELLED';
CREATE INDEX lab_order_items_order_idx ON lab_order_items (lab_order_id);

-- ---------------------------------------------------------------------------------------------
-- Catalog reference data: common tests at typical Indian lab prices, adult reference ranges.
-- ---------------------------------------------------------------------------------------------

INSERT INTO lab_tests (code, name, category, sample_type, required_tube_type, price, turnaround_hours, prep_instructions) VALUES
    ('CBC',    'Complete Blood Count',           'Haematology',   'BLOOD', 'EDTA',      350,  4,  NULL),
    ('ESR',    'Erythrocyte Sedimentation Rate', 'Haematology',   'BLOOD', 'EDTA',      150,  4,  NULL),
    ('PTINR',  'Prothrombin Time / INR',         'Haematology',   'BLOOD', 'CITRATE',   400,  6,  'Tell the lab if you take blood thinners (warfarin, aspirin).'),
    ('BGRP',   'Blood Group & Rh Type',          'Haematology',   'BLOOD', 'EDTA',      150,  4,  NULL),
    ('FBS',    'Fasting Blood Sugar',            'Biochemistry',  'BLOOD', 'FLUORIDE',  100,  4,  'Fast for 8–10 hours before the test. Water is fine.'),
    ('PPBS',   'Post-Prandial Blood Sugar',      'Biochemistry',  'BLOOD', 'FLUORIDE',  100,  4,  'Give the sample exactly 2 hours after starting a normal meal.'),
    ('HBA1C',  'HbA1c (Glycated Haemoglobin)',   'Biochemistry',  'BLOOD', 'EDTA',      550,  24, NULL),
    ('LIPID',  'Lipid Profile',                  'Biochemistry',  'BLOOD', 'SST',       600,  12, 'Fast for 10–12 hours before the test. Water is fine; avoid alcohol for 24 hours.'),
    ('LFT',    'Liver Function Test',            'Biochemistry',  'BLOOD', 'SST',       700,  12, 'Fast for 8 hours if possible.'),
    ('KFT',    'Kidney Function Test',           'Biochemistry',  'BLOOD', 'SST',       650,  12, NULL),
    ('ELEC',   'Serum Electrolytes',             'Biochemistry',  'BLOOD', 'SST',       450,  6,  NULL),
    ('CRP',    'C-Reactive Protein',             'Biochemistry',  'BLOOD', 'SST',       450,  12, NULL),
    ('VITD',   'Vitamin D (25-OH)',              'Biochemistry',  'BLOOD', 'SST',       1400, 48, NULL),
    ('VITB12', 'Vitamin B12',                    'Biochemistry',  'BLOOD', 'SST',       1000, 48, NULL),
    ('THY',    'Thyroid Profile (T3, T4, TSH)',  'Endocrinology', 'BLOOD', 'SST',       550,  24, 'Take the sample before your morning thyroid tablet, if you take one.'),
    ('TSH',    'TSH',                            'Endocrinology', 'BLOOD', 'SST',       300,  24, 'Take the sample before your morning thyroid tablet, if you take one.'),
    ('NS1',    'Dengue NS1 Antigen',             'Serology',      'BLOOD', 'SST',       600,  6,  NULL),
    ('WIDAL',  'Widal Test',                     'Serology',      'BLOOD', 'SST',       250,  12, NULL),
    ('HBSAG',  'Hepatitis B Surface Antigen',    'Serology',      'BLOOD', 'SST',       400,  12, NULL),
    ('URINE',  'Urine Routine & Microscopy',     'Clinical Pathology', 'URINE', 'URINE_CUP', 150, 4, 'Collect the first urine of the morning, midstream, in the sterile container from the lab.'),
    ('UCS',    'Urine Culture & Sensitivity',    'Microbiology',  'URINE', 'URINE_CUP', 700,  72, 'Collect a clean-catch midstream sample, ideally before starting antibiotics.'),
    ('STOOL',  'Stool Routine',                  'Clinical Pathology', 'STOOL', 'STOOL_CUP', 200, 12, 'Collect a fresh sample in the container from the lab and bring it within 2 hours.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO lab_test_parameters (lab_test_id, position, name, unit, ref_low, ref_high, critical_low, critical_high)
SELECT t.id, p.position, p.name, p.unit, p.ref_low, p.ref_high, p.critical_low, p.critical_high
FROM (VALUES
    ('CBC',    1, 'Haemoglobin',              'g/dL',        12.0,  15.5,  7.0,   20.0),
    ('CBC',    2, 'Total WBC count',          '×10³/µL',     4.0,   11.0,  2.0,   30.0),
    ('CBC',    3, 'Platelet count',           '×10³/µL',     150,   410,   20,    1000),
    ('CBC',    4, 'RBC count',                '×10⁶/µL',     4.2,   5.4,   NULL,  NULL),
    ('CBC',    5, 'Haematocrit',              '%',           36,    46,    20,    60),
    ('ESR',    1, 'ESR',                      'mm/hr',       0,     20,    NULL,  NULL),
    ('PTINR',  1, 'Prothrombin time',         'seconds',     11.0,  13.5,  NULL,  NULL),
    ('PTINR',  2, 'INR',                      NULL,          0.8,   1.1,   NULL,  5.0),
    ('BGRP',   1, 'Blood group',              NULL,          NULL,  NULL,  NULL,  NULL),
    ('BGRP',   2, 'Rh type',                  NULL,          NULL,  NULL,  NULL,  NULL),
    ('FBS',    1, 'Fasting glucose',          'mg/dL',       70,    99,    40,    400),
    ('PPBS',   1, 'Post-prandial glucose',    'mg/dL',       70,    140,   40,    400),
    ('HBA1C',  1, 'HbA1c',                    '%',           4.0,   5.6,   NULL,  NULL),
    ('LIPID',  1, 'Total cholesterol',        'mg/dL',       0,     200,   NULL,  NULL),
    ('LIPID',  2, 'LDL cholesterol',          'mg/dL',       0,     130,   NULL,  NULL),
    ('LIPID',  3, 'HDL cholesterol',          'mg/dL',       40,    60,    NULL,  NULL),
    ('LIPID',  4, 'Triglycerides',            'mg/dL',       0,     150,   NULL,  1000),
    ('LFT',    1, 'Total bilirubin',          'mg/dL',       0.3,   1.2,   NULL,  15.0),
    ('LFT',    2, 'ALT (SGPT)',               'U/L',         7,     56,    NULL,  NULL),
    ('LFT',    3, 'AST (SGOT)',               'U/L',         10,    40,    NULL,  NULL),
    ('LFT',    4, 'Alkaline phosphatase',     'U/L',         44,    147,   NULL,  NULL),
    ('LFT',    5, 'Albumin',                  'g/dL',        3.5,   5.0,   NULL,  NULL),
    ('KFT',    1, 'Urea',                     'mg/dL',       15,    40,    NULL,  NULL),
    ('KFT',    2, 'Creatinine',               'mg/dL',       0.7,   1.3,   NULL,  10.0),
    ('KFT',    3, 'Uric acid',                'mg/dL',       3.5,   7.2,   NULL,  NULL),
    ('KFT',    4, 'Sodium',                   'mmol/L',      135,   145,   120,   160),
    ('KFT',    5, 'Potassium',                'mmol/L',      3.5,   5.1,   2.5,   6.5),
    ('ELEC',   1, 'Sodium',                   'mmol/L',      135,   145,   120,   160),
    ('ELEC',   2, 'Potassium',                'mmol/L',      3.5,   5.1,   2.5,   6.5),
    ('ELEC',   3, 'Chloride',                 'mmol/L',      98,    107,   NULL,  NULL),
    ('CRP',    1, 'CRP',                      'mg/L',        0,     6,     NULL,  NULL),
    ('VITD',   1, '25-OH Vitamin D',          'ng/mL',       30,    100,   NULL,  NULL),
    ('VITB12', 1, 'Vitamin B12',              'pg/mL',       200,   900,   NULL,  NULL),
    ('THY',    1, 'Free T3',                  'pg/mL',       2.3,   4.2,   NULL,  NULL),
    ('THY',    2, 'Free T4',                  'ng/dL',       0.8,   1.8,   NULL,  NULL),
    ('THY',    3, 'TSH',                      'mIU/L',       0.4,   4.0,   NULL,  NULL),
    ('TSH',    1, 'TSH',                      'mIU/L',       0.4,   4.0,   NULL,  NULL),
    ('NS1',    1, 'Dengue NS1 antigen',       NULL,          NULL,  NULL,  NULL,  NULL),
    ('WIDAL',  1, 'S. Typhi O titre',         NULL,          NULL,  NULL,  NULL,  NULL),
    ('WIDAL',  2, 'S. Typhi H titre',         NULL,          NULL,  NULL,  NULL,  NULL),
    ('HBSAG',  1, 'HBsAg',                    NULL,          NULL,  NULL,  NULL,  NULL),
    ('URINE',  1, 'pH',                       NULL,          4.5,   8.0,   NULL,  NULL),
    ('URINE',  2, 'Specific gravity',         NULL,          1.005, 1.030, NULL,  NULL),
    ('URINE',  3, 'Protein',                  NULL,          NULL,  NULL,  NULL,  NULL),
    ('URINE',  4, 'Glucose',                  NULL,          NULL,  NULL,  NULL,  NULL),
    ('URINE',  5, 'Pus cells',                '/hpf',        0,     5,     NULL,  NULL),
    ('UCS',    1, 'Organism isolated',        NULL,          NULL,  NULL,  NULL,  NULL),
    ('UCS',    2, 'Colony count',             'CFU/mL',      NULL,  NULL,  NULL,  NULL),
    ('STOOL',  1, 'Occult blood',             NULL,          NULL,  NULL,  NULL,  NULL),
    ('STOOL',  2, 'Ova and cysts',            NULL,          NULL,  NULL,  NULL,  NULL)
) AS p (code, position, name, unit, ref_low, ref_high, critical_low, critical_high)
JOIN lab_tests t ON t.code = p.code
ON CONFLICT (lab_test_id, position) DO NOTHING;
