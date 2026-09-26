-- Phase 01: identity and roles (see Docs/Schema.md §1, ADR-011).
-- One role per account. Each profile table pins the role it belongs to through a
-- composite foreign key on (user_id, role), so e.g. a DOCTOR profile can never point
-- at a PATIENT account, even if application code has a bug.

CREATE TABLE users (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(20)  NOT NULL,
    is_active     boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT users_role_ck CHECK (role IN ('PATIENT', 'DOCTOR', 'PATHOLOGIST', 'RECEPTIONIST', 'LAB_TECHNICIAN', 'ADMIN')),
    CONSTRAINT users_id_role_uq UNIQUE (id, role)
);

-- Emails are compared case-insensitively.
CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));

CREATE TABLE patients (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Nullable: the front desk can register a walk-in who has no login yet.
    user_id         uuid         UNIQUE,
    user_role       varchar(20)  NOT NULL DEFAULT 'PATIENT' CHECK (user_role = 'PATIENT'),
    full_name       varchar(200) NOT NULL,
    dob             date         NOT NULL,
    gender          varchar(10)  NOT NULL CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    phone           varchar(20)  NOT NULL,
    address         text,
    known_allergies text,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT patients_user_fk FOREIGN KEY (user_id, user_role) REFERENCES users (id, role)
);

CREATE TABLE doctors (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid         NOT NULL UNIQUE,
    user_role      varchar(20)  NOT NULL DEFAULT 'DOCTOR' CHECK (user_role = 'DOCTOR'),
    full_name      varchar(200) NOT NULL,
    specialization varchar(120) NOT NULL,
    CONSTRAINT doctors_user_fk FOREIGN KEY (user_id, user_role) REFERENCES users (id, role)
);

CREATE TABLE pathologists (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid         NOT NULL UNIQUE,
    user_role           varchar(20)  NOT NULL DEFAULT 'PATHOLOGIST' CHECK (user_role = 'PATHOLOGIST'),
    full_name           varchar(200) NOT NULL,
    qualification       varchar(120) NOT NULL,
    registration_number varchar(60)  NOT NULL UNIQUE,
    signature_image_url text,
    CONSTRAINT pathologists_user_fk FOREIGN KEY (user_id, user_role) REFERENCES users (id, role)
);

CREATE TABLE staff (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid         NOT NULL UNIQUE,
    full_name  varchar(200) NOT NULL,
    staff_type varchar(20)  NOT NULL CHECK (staff_type IN ('RECEPTIONIST', 'LAB_TECHNICIAN', 'ADMIN')),
    -- staff_type doubles as the user's role, so it must match users.role.
    CONSTRAINT staff_user_fk FOREIGN KEY (user_id, staff_type) REFERENCES users (id, role)
);
