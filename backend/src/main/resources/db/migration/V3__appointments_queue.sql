-- Phase 03: appointments & queue — doctor working hours, booking, walk-in tokens, status lifecycle,
-- reminders. See Docs/Schema.md §2, Docs/Rules.md §1a and ADR-020.

-- ---------------------------------------------------------------------------------------------
-- Doctor working hours: weekly blocks cut into bookable slots (e.g. Mon 09:00–13:00, 15 min).
-- A doctor may have several blocks a day (morning and evening clinics).
-- ---------------------------------------------------------------------------------------------

CREATE TABLE doctor_schedules (
    id           uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id    uuid     NOT NULL REFERENCES doctors (id) ON DELETE CASCADE,
    day_of_week  smallint NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- ISO: 1 = Monday
    start_time   time     NOT NULL,
    end_time     time     NOT NULL,
    slot_minutes smallint NOT NULL DEFAULT 15 CHECK (slot_minutes BETWEEN 5 AND 120),
    CHECK (end_time > start_time),
    UNIQUE (doctor_id, day_of_week, start_time)
);

-- ---------------------------------------------------------------------------------------------
-- Appointments: booked slots and walk-in tokens share one table, told apart by `kind`.
-- ---------------------------------------------------------------------------------------------

ALTER TABLE appointments
    ADD COLUMN kind varchar(10) NOT NULL DEFAULT 'SCHEDULED' CHECK (kind IN ('SCHEDULED', 'WALK_IN')),
    ADD COLUMN duration_minutes smallint NOT NULL DEFAULT 15 CHECK (duration_minutes BETWEEN 5 AND 120),
    ADD COLUMN reason varchar(500),
    -- Queue position: numbered per clinic day in check-in order; `queue_token` is its display form (T-007).
    ADD COLUMN queue_date date,
    ADD COLUMN queue_number integer CHECK (queue_number > 0),
    ADD COLUMN booked_by_user_id uuid REFERENCES users (id),
    ADD COLUMN checked_in_at timestamptz,
    ADD COLUMN started_at timestamptz,
    ADD COLUMN completed_at timestamptz,
    ADD COLUMN cancelled_at timestamptz,
    ADD COLUMN cancellation_reason varchar(300),
    ADD COLUMN reminder_sent_at timestamptz,
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
    ADD CONSTRAINT appointments_token_complete
        CHECK ((queue_number IS NULL) = (queue_date IS NULL) AND (queue_number IS NULL) = (queue_token IS NULL)),
    ADD CONSTRAINT appointments_walk_in_has_token CHECK (kind <> 'WALK_IN' OR queue_number IS NOT NULL);

-- One token number per clinic day.
CREATE UNIQUE INDEX appointments_queue_number_uq ON appointments (queue_date, queue_number)
    WHERE queue_number IS NOT NULL;

-- No double booking: one live booking per doctor per slot. Cancelling frees the slot.
CREATE UNIQUE INDEX appointments_slot_uq ON appointments (doctor_id, scheduled_at)
    WHERE kind = 'SCHEDULED' AND status <> 'CANCELLED';

CREATE INDEX appointments_patient_time_idx ON appointments (patient_id, scheduled_at);
CREATE INDEX appointments_queue_idx ON appointments (queue_date, doctor_id) WHERE queue_date IS NOT NULL;
CREATE INDEX appointments_reminder_due_idx ON appointments (scheduled_at)
    WHERE status = 'BOOKED' AND reminder_sent_at IS NULL;

-- ---------------------------------------------------------------------------------------------
-- Status history: append-only, one row per change (who, from, to, when).
-- ---------------------------------------------------------------------------------------------

CREATE TABLE appointment_events (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id     uuid        NOT NULL REFERENCES appointments (id),
    from_status        varchar(20),
    to_status          varchar(20) NOT NULL,
    changed_by_user_id uuid        REFERENCES users (id),
    note               varchar(300),
    created_at         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX appointment_events_appointment_idx ON appointment_events (appointment_id, created_at);

CREATE TRIGGER appointment_events_append_only
    BEFORE UPDATE OR DELETE ON appointment_events
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();
