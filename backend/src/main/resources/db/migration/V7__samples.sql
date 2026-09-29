-- Phase 07: sample lifecycle — collection, receipt check, rejection and redraw. See Docs/Schema.md §3
-- and ADR-024.
-- A sample is one physical tube (or cup): all of an order's tests that need the same tube share it.
-- The rules that protect the chain of custody live here, not only in Java: transitions can only go
-- the allowed way, the event log and rejection records can't be rewritten, samples are never deleted.

-- ---------------------------------------------------------------------------------------------
-- Samples
-- ---------------------------------------------------------------------------------------------

-- Daily counter behind LAB-YYYYMMDD-####. One row per clinic day, bumped atomically.
CREATE TABLE sample_code_counters (
    day         date PRIMARY KEY,
    last_number integer NOT NULL
);

CREATE TABLE samples (
    id                    uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    sample_code           varchar(20)  NOT NULL UNIQUE,
    lab_order_id          uuid         NOT NULL REFERENCES lab_orders (id),
    -- Denormalised so "this patient's samples" and access checks need no join through the order.
    patient_id            uuid         NOT NULL REFERENCES patients (id),
    required_tube_type    varchar(20)  NOT NULL
        CHECK (required_tube_type IN ('EDTA', 'PLAIN', 'SST', 'CITRATE', 'FLUORIDE', 'HEPARIN', 'URINE_CUP', 'STOOL_CUP', 'SWAB_TUBE')),
    status                varchar(20)  NOT NULL DEFAULT 'ORDERED'
        CHECK (status IN ('ORDERED', 'COLLECTED', 'RECEIVED_AT_LAB', 'IN_TESTING', 'RESULT_ENTERED', 'VERIFIED',
                          'REPORT_GENERATED', 'DISPATCHED', 'REJECTED', 'CANCELLED')),
    tube_type_used        varchar(20)
        CHECK (tube_type_used IS NULL OR tube_type_used IN
               ('EDTA', 'PLAIN', 'SST', 'CITRATE', 'FLUORIDE', 'HEPARIN', 'URINE_CUP', 'STOOL_CUP', 'SWAB_TUBE')),
    tube_mismatch         boolean      NOT NULL DEFAULT false,
    body_site             varchar(60),
    collected_at          timestamptz,
    collected_by_user_id  uuid         REFERENCES users (id),
    received_at           timestamptz,
    received_by_user_id   uuid         REFERENCES users (id),
    -- A redraw points back at the rejected sample it replaces (the rejected one is never reused).
    redraw_of_sample_id   uuid         REFERENCES samples (id),
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    -- Collection details exist exactly once the sample has been collected.
    CHECK ((status IN ('ORDERED', 'CANCELLED') AND collected_at IS NULL)
           OR (status NOT IN ('ORDERED', 'CANCELLED') AND collected_at IS NOT NULL AND tube_type_used IS NOT NULL
               AND collected_by_user_id IS NOT NULL) )
);

CREATE INDEX samples_status_idx ON samples (status, created_at) WHERE status IN ('ORDERED', 'COLLECTED');
CREATE INDEX samples_order_idx ON samples (lab_order_id);
CREATE INDEX samples_patient_idx ON samples (patient_id, created_at DESC);

-- The tests a sample covers. A rejected sample keeps its rows, so its history stays intact.
CREATE TABLE sample_items (
    sample_id          uuid NOT NULL REFERENCES samples (id),
    lab_order_item_id  uuid NOT NULL REFERENCES lab_order_items (id),
    PRIMARY KEY (sample_id, lab_order_item_id)
);

CREATE INDEX sample_items_item_idx ON sample_items (lab_order_item_id);

-- Only these moves are allowed (Docs/Rules.md §2): forward through the pipeline, rejection from the
-- receipt check or from testing, and return for retest (RESULT_ENTERED -> IN_TESTING).
CREATE FUNCTION enforce_sample_transition() RETURNS trigger AS $$
BEGIN
    IF NEW.status = OLD.status THEN
        RETURN NEW;
    END IF;
    IF (OLD.status, NEW.status) IN (
        ('ORDERED', 'COLLECTED'), ('ORDERED', 'CANCELLED'),
        ('COLLECTED', 'RECEIVED_AT_LAB'), ('COLLECTED', 'REJECTED'),
        ('RECEIVED_AT_LAB', 'IN_TESTING'),
        ('IN_TESTING', 'RESULT_ENTERED'), ('IN_TESTING', 'REJECTED'),
        ('RESULT_ENTERED', 'VERIFIED'), ('RESULT_ENTERED', 'IN_TESTING'),
        ('VERIFIED', 'REPORT_GENERATED'), ('REPORT_GENERATED', 'DISPATCHED')
    ) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'A sample cannot move from % to %', OLD.status, NEW.status;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER samples_transition
    BEFORE UPDATE OF status ON samples
    FOR EACH ROW EXECUTE FUNCTION enforce_sample_transition();

CREATE FUNCTION forbid_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are permanent records and cannot be deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER samples_no_delete
    BEFORE DELETE ON samples
    FOR EACH ROW EXECUTE FUNCTION forbid_delete();

-- ---------------------------------------------------------------------------------------------
-- Chain of custody (append-only)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE sample_status_events (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    sample_id      uuid         NOT NULL REFERENCES samples (id),
    status         varchar(20)  NOT NULL
        CHECK (status IN ('ORDERED', 'COLLECTED', 'RECEIVED_AT_LAB', 'IN_TESTING', 'RESULT_ENTERED', 'VERIFIED',
                          'REPORT_GENERATED', 'DISPATCHED', 'REJECTED', 'CANCELLED')),
    actor_user_id  uuid         NOT NULL REFERENCES users (id),
    occurred_at    timestamptz  NOT NULL DEFAULT now(),
    detail         varchar(500)
);

CREATE INDEX sample_status_events_sample_idx ON sample_status_events (sample_id, occurred_at);

CREATE TRIGGER sample_status_events_append_only
    BEFORE UPDATE OR DELETE ON sample_status_events
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();

-- ---------------------------------------------------------------------------------------------
-- Rejections (permanent) and front-desk notifications
-- ---------------------------------------------------------------------------------------------

CREATE TABLE rejection_records (
    id                     uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    sample_id              uuid         NOT NULL UNIQUE REFERENCES samples (id),
    -- Built with the stage from the start: Phase 08 adds rejection during testing on top.
    rejected_at_stage      varchar(20)  NOT NULL CHECK (rejected_at_stage IN ('RECEIVED_AT_LAB', 'IN_TESTING')),
    reason                 varchar(24)  NOT NULL,
    note                   varchar(500),
    flagged_by_user_id     uuid         NOT NULL REFERENCES users (id),
    flagged_at             timestamptz  NOT NULL DEFAULT now(),
    front_desk_notified_at timestamptz,
    -- Which reasons are allowed at which stage (Rules.md §2); OTHER needs a note.
    CHECK ((rejected_at_stage = 'RECEIVED_AT_LAB' AND reason IN ('HEMOLYZED', 'CLOTTED', 'INSUFFICIENT_VOLUME', 'OTHER'))
           OR (rejected_at_stage = 'IN_TESTING' AND reason IN ('SAMPLE_EXHAUSTED', 'SAMPLE_DEGRADED', 'OTHER'))),
    CHECK (reason <> 'OTHER' OR (note IS NOT NULL AND length(btrim(note)) > 0))
);

CREATE TRIGGER rejection_records_append_only
    BEFORE UPDATE OR DELETE ON rejection_records
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();

-- A short list of things a role needs to act on. Sample rejections go to the front desk, who mark
-- them handled once the patient has been called back.
CREATE TABLE notifications (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    audience_role       varchar(20)  NOT NULL
        CHECK (audience_role IN ('PATIENT', 'DOCTOR', 'PATHOLOGIST', 'RECEPTIONIST', 'LAB_TECHNICIAN', 'ADMIN')),
    type                varchar(30)  NOT NULL CHECK (type IN ('SAMPLE_REJECTED')),
    title               varchar(120) NOT NULL,
    message             varchar(500) NOT NULL,
    patient_id          uuid         REFERENCES patients (id),
    sample_id           uuid         REFERENCES samples (id),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    handled_at          timestamptz,
    handled_by_user_id  uuid         REFERENCES users (id),
    CHECK ((handled_at IS NULL) = (handled_by_user_id IS NULL))
);

CREATE INDEX notifications_open_idx ON notifications (audience_role, created_at DESC) WHERE handled_at IS NULL;
